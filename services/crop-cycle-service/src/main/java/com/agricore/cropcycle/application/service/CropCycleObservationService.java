package com.agricore.cropcycle.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcycle.api.request.CreateCropCycleObservationRequest;
import com.agricore.cropcycle.api.response.CropCycleObservationResponse;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleObservationJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleObservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CropCycleObservationService {

    private final CropCycleObservationJpaRepository observationRepository;
    private final CropCycleJpaRepository cycleRepository;
    private final CropCycleAccessGuard accessGuard;

    public CropCycleObservationService(
            CropCycleObservationJpaRepository observationRepository,
            CropCycleJpaRepository cycleRepository,
            CropCycleAccessGuard accessGuard
    ) {
        this.observationRepository = observationRepository;
        this.cycleRepository = cycleRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public CropCycleObservationResponse create(
            UUID cropCycleId,
            CreateCropCycleObservationRequest request,
            String actor
    ) {
        requireCycleAccess(cropCycleId);
        CropCycleObservationEntity observation = new CropCycleObservationEntity();
        observation.setId(UUID.randomUUID());
        observation.setCropCycleId(cropCycleId);
        observation.setCategory(request.category());
        observation.setSeverity(request.severity());
        observation.setTitle(request.title().trim());
        observation.setDetails(request.details().trim());
        observation.setObservedAt(request.observedAt());
        observation.setRecordedBy(AuthenticatedActor.requireValid(actor));
        observation.setCreatedAt(Instant.now());
        return toResponse(observationRepository.save(observation));
    }

    @Transactional(readOnly = true)
    public PageResponse<CropCycleObservationResponse> list(UUID cropCycleId, Pageable pageable) {
        requireCycleAccess(cropCycleId);
        Page<CropCycleObservationEntity> page = observationRepository.findByCropCycleId(cropCycleId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private void requireCycleAccess(UUID cropCycleId) {
        CropCycleEntity cycle = cycleRepository.findById(cropCycleId)
                .orElseThrow(() -> new CropCycleException("CYCLE_NOT_FOUND", "Crop cycle not found", 404));
        accessGuard.requireFarmPlot(cycle.getFarmId(), cycle.getPlotId());
    }

    private CropCycleObservationResponse toResponse(CropCycleObservationEntity observation) {
        return new CropCycleObservationResponse(
                observation.getId(),
                observation.getCropCycleId(),
                observation.getCategory(),
                observation.getSeverity(),
                observation.getTitle(),
                observation.getDetails(),
                observation.getObservedAt(),
                observation.getRecordedBy(),
                observation.getCreatedAt()
        );
    }
}
