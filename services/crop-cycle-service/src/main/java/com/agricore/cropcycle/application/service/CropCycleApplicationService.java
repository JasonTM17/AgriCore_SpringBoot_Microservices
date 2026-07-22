package com.agricore.cropcycle.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.domain.model.CycleStatus;
import com.agricore.cropcycle.domain.policy.CycleStageTransitionPolicy;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CropCycleApplicationService {

    private static final Set<CycleStage> TERMINAL = EnumSet.of(CycleStage.COMPLETED, CycleStage.CANCELLED);
    private static final Set<CycleStatus> ACTIVE_STATUSES = EnumSet.of(CycleStatus.DRAFT, CycleStatus.ACTIVE);
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private final CropCycleJpaRepository cycleRepository;
    private final CropCycleOutboxWriter outboxWriter;
    private final CropCycleAccessGuard accessGuard;

    public CropCycleApplicationService(
            CropCycleJpaRepository cycleRepository,
            CropCycleOutboxWriter outboxWriter,
            CropCycleAccessGuard accessGuard
    ) {
        this.cycleRepository = cycleRepository;
        this.outboxWriter = outboxWriter;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public CropCycleResponse create(CreateCropCycleRequest request) {
        accessGuard.requireFarmPlot(request.farmId(), request.plotId());
        String code = request.code().trim().toUpperCase();
        if (cycleRepository.existsByCodeIgnoreCase(code)) {
            throw new CropCycleException("CYCLE_CODE_EXISTS", "Crop cycle code already exists", 409);
        }
        if (request.plannedEndDate() != null && request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw new CropCycleException("INVALID_DATES", "plannedEndDate must be on or after plannedStartDate", 400);
        }

        LocalDate newStart = request.plannedStartDate();
        LocalDate newEnd = request.plannedEndDate() == null ? OPEN_END : request.plannedEndDate();
        List<CropCycleEntity> overlaps = cycleRepository.findOverlappingActiveCycles(
                request.plotId(),
                ACTIVE_STATUSES,
                newStart,
                newEnd,
                OPEN_END
        );
        if (!overlaps.isEmpty()) {
            throw new CropCycleException(
                    "CROP_CYCLE_OVERLAP",
                    "Plot already has an active crop cycle overlapping the requested dates",
                    409
            );
        }

        Instant now = Instant.now();
        CropCycleEntity cycle = new CropCycleEntity();
        cycle.setId(UUID.randomUUID());
        cycle.setCode(code);
        cycle.setFarmId(request.farmId());
        cycle.setPlotId(request.plotId());
        cycle.setCropId(request.cropId());
        cycle.setCropVarietyId(request.cropVarietyId());
        cycle.setPlannedStartDate(request.plannedStartDate());
        cycle.setPlannedEndDate(request.plannedEndDate());
        cycle.setStage(CycleStage.PLANNED);
        cycle.setStatus(CycleStatus.DRAFT);
        cycle.setNotes(request.notes());
        cycle.setCreatedAt(now);
        cycle.setUpdatedAt(now);
        cycleRepository.save(cycle);

        outboxWriter.enqueue(EventTypes.CROP_CYCLE_CREATED, cycle, null);
        return toResponse(cycle);
    }

    @Transactional(readOnly = true)
    public CropCycleResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CropCycleResponse> list(UUID farmId, UUID plotId, Pageable pageable) {
        accessGuard.requireListScope(farmId, plotId);
        Page<CropCycleEntity> page;
        if (farmId != null && plotId != null) {
            page = cycleRepository.findByFarmIdAndPlotId(farmId, plotId, pageable);
        } else if (plotId != null) {
            page = cycleRepository.findByPlotId(plotId, pageable);
        } else if (farmId != null) {
            page = cycleRepository.findByFarmId(farmId, pageable);
        } else {
            page = cycleRepository.findAll(pageable);
        }
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public CropCycleResponse changeStage(UUID id, ChangeStageRequest request) {
        CropCycleEntity cycle = require(id);
        CycleStage previous = cycle.getStage();
        String requestedStage = request.stage().trim().toUpperCase(Locale.ROOT);
        if (previous.name().equals(requestedStage)) {
            return toResponse(cycle);
        }
        if (TERMINAL.contains(previous)) {
            throw new CropCycleException("CYCLE_TERMINAL", "Cannot change stage of a terminal cycle", 409);
        }

        CycleStage next;
        try {
            next = CycleStage.valueOf(requestedStage);
        } catch (IllegalArgumentException ex) {
            throw new CropCycleException("INVALID_STAGE", "Unknown stage: " + request.stage(), 400);
        }

        if (!CycleStageTransitionPolicy.canTransition(previous, next)) {
            throw new CropCycleException(
                    "INVALID_STAGE_TRANSITION",
                    "Illegal stage transition from " + previous + " to " + next
                            + ". Allowed: " + CycleStageTransitionPolicy.allowedNext(previous),
                    409
            );
        }

        cycle.setStage(next);
        if (request.notes() != null) {
            cycle.setNotes(request.notes());
        }
        if (cycle.getActualStartDate() == null && next != CycleStage.PLANNED) {
            cycle.setActualStartDate(LocalDate.now());
            cycle.setStatus(CycleStatus.ACTIVE);
        }

        String eventType = EventTypes.CROP_CYCLE_STAGE_CHANGED;
        if (next == CycleStage.COMPLETED) {
            cycle.setStatus(CycleStatus.COMPLETED);
            cycle.setActualEndDate(LocalDate.now());
            eventType = EventTypes.CROP_CYCLE_COMPLETED;
        } else if (next == CycleStage.CANCELLED) {
            cycle.setStatus(CycleStatus.CANCELLED);
            cycle.setActualEndDate(LocalDate.now());
            eventType = EventTypes.CROP_CYCLE_CANCELLED;
        }

        cycle.setUpdatedAt(Instant.now());
        cycle = cycleRepository.saveAndFlush(cycle);
        outboxWriter.enqueue(eventType, cycle, previous.name());
        return toResponse(cycle);
    }

    @Transactional
    public CropCycleResponse cancel(UUID id) {
        return changeStage(id, new ChangeStageRequest(CycleStage.CANCELLED.name(), null));
    }

    private CropCycleEntity require(UUID id) {
        CropCycleEntity cycle = cycleRepository.findById(id)
                .orElseThrow(() -> new CropCycleException("CYCLE_NOT_FOUND", "Crop cycle not found", 404));
        accessGuard.requireFarmPlot(cycle.getFarmId(), cycle.getPlotId());
        return cycle;
    }

    private CropCycleResponse toResponse(CropCycleEntity c) {
        return new CropCycleResponse(
                c.getId(), c.getCode(), c.getFarmId(), c.getPlotId(), c.getCropId(), c.getCropVarietyId(),
                c.getPlannedStartDate(), c.getPlannedEndDate(), c.getActualStartDate(), c.getActualEndDate(),
                c.getStage().name(), c.getStatus().name(), c.getNotes(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getVersion()
        );
    }
}
