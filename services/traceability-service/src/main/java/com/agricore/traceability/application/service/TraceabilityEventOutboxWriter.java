package com.agricore.traceability.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.traceability.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class TraceabilityEventOutboxWriter {

    private static final String TOPIC = "agricore.traceability.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TraceabilityEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void traceabilityBatchCreated(TraceabilityBatchEntity batch) {
        ObjectNode payload = commonPayload(batch);
        putOptional(payload, "cropCycleId", batch.getCropCycleId());
        putOptional(payload, "plotId", batch.getPlotId());
        putOptional(payload, "farmName", batch.getFarmName());
        putOptional(payload, "plotCode", batch.getPlotCode());
        putOptional(payload, "varietyName", batch.getVarietyName());
        putOptional(payload, "plantingDate", batch.getPlantingDate());
        putOptional(payload, "qualityGrade", batch.getQualityGrade());
        if (batch.getNetWeightKg() != null) {
            payload.put("netWeightKg", batch.getNetWeightKg());
        }
        putOptional(payload, "careSummary", batch.getCareSummary());
        enqueue(EventTypes.TRACEABILITY_BATCH_CREATED, batch, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void traceabilityCodeGenerated(TraceabilityBatchEntity batch) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("traceabilityBatchId", batch.getId().toString());
        payload.put("traceabilityCode", batch.getTraceabilityCode());
        payload.put("publicUrl", batch.getQrUrl());
        payload.put("qrImageUrl", batch.getQrUrl() + "/qr");
        payload.put("batchLabel", batchLabel(batch));
        payload.put("generatedAt", batch.getCreatedAt().toString());
        enqueue(EventTypes.TRACEABILITY_CODE_GENERATED, batch, payload);
    }

    private ObjectNode commonPayload(TraceabilityBatchEntity batch) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("traceabilityBatchId", batch.getId().toString());
        payload.put("traceabilityCode", batch.getTraceabilityCode());
        payload.put("harvestBatchId", batch.getHarvestBatchId().toString());
        payload.put("productName", batch.getProductName());
        payload.put("harvestDate", batch.getHarvestDate().toString());
        payload.put("publicUrl", batch.getQrUrl());
        payload.put("batchLabel", batchLabel(batch));
        payload.put("createdAt", batch.getCreatedAt().toString());
        return payload;
    }

    private void enqueue(String eventType, TraceabilityBatchEntity batch, ObjectNode payload) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", batch.getCreatedAt().toString());
        envelope.put("producer", "traceability-service");
        envelope.set("payload", payload);
        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    "TraceabilityBatch",
                    batch.getId().toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize traceability outbox event", exception);
        }
    }

    private static String batchLabel(TraceabilityBatchEntity batch) {
        return "BATCH-" + batch.getTraceabilityCode();
    }

    private static void putOptional(ObjectNode payload, String field, Object value) {
        if (value instanceof UUID uuid) {
            payload.put(field, uuid.toString());
        } else if (value instanceof LocalDate date) {
            payload.put(field, date.toString());
        } else if (value instanceof String text) {
            payload.put(field, text);
        }
    }
}
