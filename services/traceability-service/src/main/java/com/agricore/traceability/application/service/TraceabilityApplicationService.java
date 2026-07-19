package com.agricore.traceability.application.service;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
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
public class TraceabilityApplicationService {

    public static final String HARVEST_CONSUMER = "traceability-harvest-completed";

    private final TraceabilityBatchJpaRepository batchRepository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final String publicBaseUrl;

    public TraceabilityApplicationService(
            TraceabilityBatchJpaRepository batchRepository,
            ProcessedEventJpaRepository processedEventRepository,
            @Value("${agricore.public-base-url}") String publicBaseUrl
    ) {
        this.batchRepository = batchRepository;
        this.processedEventRepository = processedEventRepository;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional
    public PublicTraceabilityResponse createFromHarvest(CreateTraceabilityRequest request) {
        String eventId = request.eventId().toString();
        if (processedEventRepository.findCanonicalOrLegacy(eventId, HARVEST_CONSUMER).isPresent()) {
            return batchRepository.findAll().stream()
                    .filter(b -> b.getHarvestBatchId().equals(request.harvestBatchId()))
                    .findFirst()
                    .map(this::toPublic)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Event processed but batch missing"));
        }

        String code = generateCode(request.productName(), request.harvestBatchId());
        TraceabilityBatchEntity batch = new TraceabilityBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setTraceabilityCode(code);
        batch.setHarvestBatchId(request.harvestBatchId());
        batch.setCropCycleId(request.cropCycleId());
        batch.setPlotId(request.plotId());
        batch.setFarmName(request.farmName());
        batch.setPlotCode(request.plotCode());
        batch.setProductName(request.productName());
        batch.setVarietyName(request.varietyName());
        batch.setPlantingDate(request.plantingDate());
        batch.setHarvestDate(request.harvestDate());
        batch.setQualityGrade(request.qualityGrade());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setCareSummary(request.careSummary());
        batch.setQrUrl(publicBaseUrl + "/" + code);
        batch.setCreatedAt(Instant.now());
        batchRepository.save(batch);
        processedEventRepository.save(ProcessedEventEntity.of(eventId, HARVEST_CONSUMER));
        return toPublic(batch);
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

    private static String generateCode(String productName, UUID harvestBatchId) {
        String prefix = productName == null ? "PRD" : productName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (prefix.length() > 6) {
            prefix = prefix.substring(0, 6);
        }
        if (prefix.isBlank()) {
            prefix = "PRD";
        }
        String suffix = harvestBatchId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return prefix + "-" + suffix;
    }
}
