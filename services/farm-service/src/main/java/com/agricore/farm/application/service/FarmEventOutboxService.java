package com.agricore.farm.application.service;

import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class FarmEventOutboxService {

    private static final Logger log = LoggerFactory.getLogger(FarmEventOutboxService.class);

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FarmEventOutboxService(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            ObjectNode payload
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "farm-service");
        envelope.set("payload", payload);
        String serializedEnvelope;
        try {
            serializedEnvelope = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize farm event {} for aggregate {}", eventType, aggregateId, ex);
            throw new FarmException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500, ex);
        }
        outboxRepository.save(OutboxEventEntity.create(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                serializedEnvelope
        ));
    }
}
