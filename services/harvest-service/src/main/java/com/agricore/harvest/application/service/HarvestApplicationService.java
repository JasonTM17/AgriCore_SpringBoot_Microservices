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

    public HarvestApplicationService(
            HarvestBatchJpaRepository harvestRepository,
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.harvestRepository = harvestRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a completed harvest batch and writes HarvestCompleted.v1 to the outbox
     * in the same database transaction (no dual-write).
     */
    @Transactional
    public HarvestBatchResponse completeHarvest(CompleteHarvestRequest request) {
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
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        harvestRepository.save(batch);

        String eventId = UUID.randomUUID().toString();
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

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId);
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

        return toResponse(batch, eventId);
    }

    @Transactional(readOnly = true)
    public HarvestBatchResponse get(UUID id) {
        HarvestBatchEntity batch = harvestRepository.findById(id)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
        return toResponse(batch, null);
    }

    private HarvestBatchResponse toResponse(HarvestBatchEntity b, String eventId) {
        return new HarvestBatchResponse(
                b.getId(), b.getCode(), b.getCropCycleId(), b.getPlotId(), b.getWarehouseId(),
                b.getProductCode(), b.getGrossWeightKg(), b.getNetWeightKg(), b.getQualityGrade(),
                b.getStatus().name(), b.getHarvestedAt(), b.getNotes(), eventId,
                b.getCreatedAt(), b.getVersion()
        );
    }
}
