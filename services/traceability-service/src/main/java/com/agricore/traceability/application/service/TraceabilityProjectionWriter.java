package com.agricore.traceability.application.service;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.ProcessedEventEntity;
import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class TraceabilityProjectionWriter {

    private final TraceabilityBatchJpaRepository batchRepository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final TraceabilityEventOutboxWriter eventWriter;
    private final String publicBaseUrl;

    public TraceabilityProjectionWriter(
            TraceabilityBatchJpaRepository batchRepository,
            ProcessedEventJpaRepository processedEventRepository,
            TraceabilityEventOutboxWriter eventWriter,
            @Value("${agricore.public-base-url}") String publicBaseUrl
    ) {
        this.batchRepository = batchRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventWriter = eventWriter;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional
    public TraceabilityBatchEntity createFromHarvest(CreateTraceabilityRequest request) {
        String eventId = request.eventId().toString();
        if (processedEventRepository.findCanonicalOrLegacy(
                eventId,
                TraceabilityApplicationService.HARVEST_CONSUMER
        ).isPresent()) {
            return existingBatchOrConflict(request.harvestBatchId());
        }

        var existingBatch = batchRepository.findByHarvestBatchIdForUpdate(request.harvestBatchId());
        if (existingBatch.isPresent()) {
            acknowledgeIfNeeded(eventId);
            return existingBatch.orElseThrow();
        }

        TraceabilityBatchEntity batch = new TraceabilityBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setTraceabilityCode(generateCode(request.productName(), request.harvestBatchId()));
        batch.setHarvestBatchId(request.harvestBatchId());
        batch.setCropCycleId(request.cropCycleId());
        batch.setPlotId(request.plotId());
        batch.setFarmName(request.farmName());
        batch.setPlotCode(request.plotCode());
        batch.setProductName(request.productName());
        batch.setProductCode(request.productCode());
        batch.setVarietyName(request.varietyName());
        batch.setPlantingDate(request.plantingDate());
        batch.setHarvestDate(request.harvestDate());
        batch.setQualityGrade(request.qualityGrade());
        batch.setGrossWeightKg(request.grossWeightKg());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setCareSummary(request.careSummary());
        batch.setQrUrl(publicBaseUrl + "/" + batch.getTraceabilityCode());
        batch.setCreatedAt(Instant.now());
        batchRepository.save(batch);
        processedEventRepository.save(ProcessedEventEntity.of(
                eventId,
                TraceabilityApplicationService.HARVEST_CONSUMER
        ));
        eventWriter.traceabilityBatchCreated(batch);
        eventWriter.traceabilityCodeGenerated(batch);
        processedEventRepository.flush();
        return batch;
    }

    @Transactional
    public TraceabilityBatchEntity acknowledgeExistingProjection(UUID eventId, UUID harvestBatchId) {
        TraceabilityBatchEntity batch = batchRepository.findByHarvestBatchIdForUpdate(harvestBatchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Concurrent projection failed without an existing batch"
                ));
        acknowledgeIfNeeded(eventId.toString());
        processedEventRepository.flush();
        return batch;
    }

    private void acknowledgeIfNeeded(String eventId) {
        if (processedEventRepository.findCanonicalOrLegacy(
                eventId,
                TraceabilityApplicationService.HARVEST_CONSUMER
        ).isEmpty()) {
            processedEventRepository.save(ProcessedEventEntity.of(
                    eventId,
                    TraceabilityApplicationService.HARVEST_CONSUMER
            ));
        }
    }

    private TraceabilityBatchEntity existingBatchOrConflict(UUID harvestBatchId) {
        return batchRepository.findFirstByHarvestBatchId(harvestBatchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Event processed but batch missing"
                ));
    }

    private static String generateCode(String productName, UUID harvestBatchId) {
        String prefix = productName == null
                ? "PRD"
                : productName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (prefix.length() > 6) {
            prefix = prefix.substring(0, 6);
        }
        if (prefix.isBlank()) {
            prefix = "PRD";
        }
        String suffix = harvestBatchId.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return prefix + "-" + suffix;
    }
}
