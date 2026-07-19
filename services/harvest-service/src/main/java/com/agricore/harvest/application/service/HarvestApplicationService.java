package com.agricore.harvest.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class HarvestApplicationService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final HarvestAccessGuard accessGuard;

    public HarvestApplicationService(
            HarvestBatchJpaRepository harvestRepository,
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            HarvestAccessGuard accessGuard
    ) {
        this.harvestRepository = harvestRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    /**
     * Records a completed harvest batch and writes HarvestCompleted.v1 to the outbox
     * in the same database transaction (no dual-write).
     */
    @Transactional
    public HarvestBatchResponse completeHarvest(CompleteHarvestRequest request) {
        accessGuard.requirePlot(request.plotId());
        String code = request.code().trim().toUpperCase();
        if (harvestRepository.existsByCodeIgnoreCase(code)) {
            throw new HarvestException("HARVEST_CODE_EXISTS", "Harvest batch code already exists", 409);
        }
        if (request.netWeightKg().compareTo(request.grossWeightKg()) > 0) {
            throw new HarvestException("INVALID_WEIGHT", "netWeightKg cannot exceed grossWeightKg", 400);
        }

        Instant now = Instant.now();
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode(code);
        batch.setCropCycleId(request.cropCycleId());
        batch.setPlotId(request.plotId());
        batch.setWarehouseId(request.warehouseId());
        batch.setProductCode(request.productCode().trim().toUpperCase());
        batch.setGrossWeightKg(request.grossWeightKg());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setQualityGrade(request.qualityGrade().trim().toUpperCase());
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setHarvestedAt(now);
        batch.setNotes(request.notes());
        UUID eventId = UUID.randomUUID();
        batch.setLastOutboxEventId(eventId);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        harvestRepository.save(batch);

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("harvestId", batch.getId().toString());
            payload.put("harvestBatchId", batch.getId().toString());
            payload.put("cropCycleId", batch.getCropCycleId().toString());
            payload.put("plotId", batch.getPlotId().toString());
            payload.put("warehouseId", batch.getWarehouseId().toString());
            payload.put("productCode", batch.getProductCode());
            payload.put("grossWeightKg", batch.getGrossWeightKg());
            payload.put("netWeightKg", batch.getNetWeightKg());
            payload.put("qualityGrade", batch.getQualityGrade());
            payload.put("harvestDate", now.toString().substring(0, 10));
            if (request.farmName() != null && !request.farmName().isBlank()) {
                payload.put("farmName", request.farmName().trim());
            }
            if (request.plotCode() != null && !request.plotCode().isBlank()) {
                payload.put("plotCode", request.plotCode().trim());
            }
            String productName = request.productName() != null && !request.productName().isBlank()
                    ? request.productName().trim()
                    : batch.getProductCode();
            payload.put("productName", productName);
            if (request.careSummary() != null && !request.careSummary().isBlank()) {
                payload.put("careSummary", request.careSummary().trim());
            }

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId.toString());
            envelope.put("eventType", EventTypes.HARVEST_COMPLETED);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", now.toString());
            envelope.put("producer", "harvest-service");
            envelope.set("payload", payload);

            outboxRepository.save(OutboxEventEntity.create(
                    "HarvestBatch",
                    batch.getId().toString(),
                    EventTypes.HARVEST_COMPLETED,
                    "agricore.harvest.events",
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new HarvestException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }

        return toResponse(batch);
    }

    @Transactional(readOnly = true)
    public HarvestBatchResponse get(UUID id) {
        HarvestBatchEntity batch = harvestRepository.findById(id)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
        accessGuard.requirePlot(batch.getPlotId());
        return toResponse(batch);
    }

    private HarvestBatchResponse toResponse(HarvestBatchEntity b) {
        return new HarvestBatchResponse(
                b.getId(), b.getCode(), b.getCropCycleId(), b.getPlotId(), b.getWarehouseId(),
                b.getProductCode(), b.getGrossWeightKg(), b.getNetWeightKg(), b.getQualityGrade(),
                b.getStatus().name(), b.getHarvestedAt(), b.getNotes(), b.getLastOutboxEventId(),
                b.getCreatedAt(), b.getVersion()
        );
    }
}
