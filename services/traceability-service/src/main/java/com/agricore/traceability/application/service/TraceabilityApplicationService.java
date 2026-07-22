package com.agricore.traceability.application.service;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@Validated
public class TraceabilityApplicationService {

    public static final String HARVEST_CONSUMER = "traceability-harvest-completed";

    private final TraceabilityBatchJpaRepository batchRepository;
    private final TraceabilityProjectionWriter projectionWriter;

    public TraceabilityApplicationService(
            TraceabilityBatchJpaRepository batchRepository,
            TraceabilityProjectionWriter projectionWriter
    ) {
        this.batchRepository = batchRepository;
        this.projectionWriter = projectionWriter;
    }

    public PublicTraceabilityResponse createFromHarvest(@Valid CreateTraceabilityRequest request) {
        try {
            return toPublic(projectionWriter.createFromHarvest(request));
        } catch (DataIntegrityViolationException exception) {
            if (batchRepository.findFirstByHarvestBatchId(request.harvestBatchId()).isEmpty()) {
                throw exception;
            }
            return toPublic(projectionWriter.acknowledgeExistingProjection(
                    request.eventId(),
                    request.harvestBatchId()
            ));
        }
    }

    @Transactional(readOnly = true)
    public PublicTraceabilityResponse getPublic(String code) {
        return batchRepository.findByTraceabilityCode(code.trim().toUpperCase(Locale.ROOT))
                .map(this::toPublic)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traceability code not found"));
    }

    private PublicTraceabilityResponse toPublic(TraceabilityBatchEntity b) {
        return new PublicTraceabilityResponse(
                b.getTraceabilityCode(),
                b.getProductName(),
                b.getVarietyName(),
                b.getFarmName(),
                b.getPlotCode(),
                b.getPlantingDate(),
                b.getHarvestDate(),
                b.getQualityGrade(),
                b.getNetWeightKg(),
                b.getCareSummary(),
                b.getQrUrl(),
                "BATCH-" + b.getTraceabilityCode()
        );
    }

}
