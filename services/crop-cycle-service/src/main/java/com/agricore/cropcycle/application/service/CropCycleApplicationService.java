package com.agricore.cropcycle.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.domain.model.CycleStatus;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import com.agricore.cropcycle.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class CropCycleApplicationService {

    private static final Set<CycleStage> TERMINAL = EnumSet.of(CycleStage.COMPLETED, CycleStage.CANCELLED);

    private final CropCycleJpaRepository cycleRepository;
    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CropCycleApplicationService(
            CropCycleJpaRepository cycleRepository,
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.cycleRepository = cycleRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CropCycleResponse create(CreateCropCycleRequest request) {
        String code = request.code().trim().toUpperCase();
        if (cycleRepository.existsByCodeIgnoreCase(code)) {
            throw new CropCycleException("CYCLE_CODE_EXISTS", "Crop cycle code already exists", 409);
        }
        if (request.plannedEndDate() != null && request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw new CropCycleException("INVALID_DATES", "plannedEndDate must be on or after plannedStartDate", 400);
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

        enqueue(EventTypes.CROP_CYCLE_CREATED, cycle, null);
        return toResponse(cycle);
    }

    @Transactional(readOnly = true)
    public CropCycleResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CropCycleResponse> list(UUID farmId, UUID plotId, Pageable pageable) {
        Page<CropCycleEntity> page;
        if (plotId != null) {
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
        if (TERMINAL.contains(cycle.getStage())) {
            throw new CropCycleException("CYCLE_TERMINAL", "Cannot change stage of a terminal cycle", 409);
        }

        CycleStage next;
        try {
            next = CycleStage.valueOf(request.stage().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CropCycleException("INVALID_STAGE", "Unknown stage: " + request.stage(), 400);
        }

        CycleStage previous = cycle.getStage();
        if (previous == next) {
            return toResponse(cycle);
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
        cycleRepository.save(cycle);
        enqueue(eventType, cycle, previous.name());
        return toResponse(cycle);
    }

    private CropCycleEntity require(UUID id) {
        return cycleRepository.findById(id)
                .orElseThrow(() -> new CropCycleException("CYCLE_NOT_FOUND", "Crop cycle not found", 404));
    }

    private void enqueue(String eventType, CropCycleEntity cycle, String previousStage) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("cropCycleId", cycle.getId().toString());
            payload.put("code", cycle.getCode());
            payload.put("farmId", cycle.getFarmId().toString());
            payload.put("plotId", cycle.getPlotId().toString());
            payload.put("cropId", cycle.getCropId().toString());
            payload.put("stage", cycle.getStage().name());
            payload.put("status", cycle.getStatus().name());
            if (previousStage != null) {
                payload.put("previousStage", previousStage);
            }

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("producer", "crop-cycle-service");
            envelope.set("payload", payload);

            outboxRepository.save(OutboxEventEntity.create(
                    "CropCycle",
                    cycle.getId().toString(),
                    eventType,
                    "agricore.crop-cycle.events",
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new CropCycleException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
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
