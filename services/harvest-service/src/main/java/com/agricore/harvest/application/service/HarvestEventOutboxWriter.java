package com.agricore.harvest.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class HarvestEventOutboxWriter {

    private static final String TOPIC = "agricore.harvest.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public HarvestEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void harvestBatchCreated(HarvestBatchEntity batch) {
        enqueue(EventTypes.HARVEST_BATCH_CREATED, batch, lifecyclePayload(batch), batch.getStartedAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void harvestStarted(HarvestBatchEntity batch) {
        enqueue(EventTypes.HARVEST_STARTED, batch, lifecyclePayload(batch), batch.getStartedAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID harvestCompleted(
            HarvestBatchEntity batch,
            String farmName,
            String plotCode,
            String productName,
            String careSummary
    ) {
        ObjectNode payload = basePayload(batch);
        payload.put("grossWeightKg", batch.getGrossWeightKg());
        payload.put("netWeightKg", batch.getNetWeightKg());
        payload.put("qualityGrade", batch.getQualityGrade());
        payload.put("harvestDate", batch.getHarvestedAt().toString().substring(0, 10));
        putTrimmed(payload, "farmName", farmName);
        putTrimmed(payload, "plotCode", plotCode);
        payload.put("productName", isBlank(productName) ? batch.getProductCode() : productName.trim());
        putTrimmed(payload, "careSummary", careSummary);
        return enqueue(EventTypes.HARVEST_COMPLETED, batch, payload, batch.getHarvestedAt());
    }

    private ObjectNode lifecyclePayload(HarvestBatchEntity batch) {
        ObjectNode payload = basePayload(batch);
        payload.put("code", batch.getCode());
        payload.put("status", batch.getStatus().name());
        payload.put("startedAt", batch.getStartedAt().toString());
        return payload;
    }

    private ObjectNode basePayload(HarvestBatchEntity batch) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("harvestId", batch.getId().toString());
        payload.put("harvestBatchId", batch.getId().toString());
        payload.put("cropCycleId", batch.getCropCycleId().toString());
        payload.put("plotId", batch.getPlotId().toString());
        payload.put("warehouseId", batch.getWarehouseId().toString());
        payload.put("productCode", batch.getProductCode());
        return payload;
    }

    private UUID enqueue(
            String eventType,
            HarvestBatchEntity batch,
            ObjectNode payload,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", "harvest-service");
        envelope.set("payload", payload);

        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    "HarvestBatch",
                    batch.getId().toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new HarvestException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }

    private static void putTrimmed(ObjectNode payload, String field, String value) {
        if (!isBlank(value)) {
            payload.put(field, value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
