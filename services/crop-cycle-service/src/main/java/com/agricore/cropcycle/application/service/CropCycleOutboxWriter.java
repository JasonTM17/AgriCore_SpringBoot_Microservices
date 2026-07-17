package com.agricore.cropcycle.application.service;

import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import com.agricore.cropcycle.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Service-local transactional outbox writer for crop-cycle domain events.
 * Keeps envelope construction out of the application service orchestrator.
 */
@Component
public class CropCycleOutboxWriter {

    public static final String TOPIC = "agricore.crop-cycle.events";
    public static final String PRODUCER = "crop-cycle-service";
    public static final String AGGREGATE_TYPE = "CropCycle";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CropCycleOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String eventType, CropCycleEntity cycle, String previousStage) {
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
            envelope.put("producer", PRODUCER);
            envelope.set("payload", payload);

            outboxRepository.save(OutboxEventEntity.create(
                    AGGREGATE_TYPE,
                    cycle.getId().toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new CropCycleException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }
}
