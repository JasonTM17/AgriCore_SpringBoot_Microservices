package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class GenerationEventFactory {

    public GenerationEventEntity create(
            ChatGenerationEntity generation,
            GenerationEventType eventType,
            String payload,
            Instant createdAt,
            Instant expiresAt
    ) {
        Objects.requireNonNull(generation, "generation is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("event expiry must follow creation time");
        }
        long sequence = generation.getNextEventSequence();
        if (sequence < 0 || sequence == Long.MAX_VALUE) {
            throw new IllegalStateException("generation event sequence is exhausted");
        }
        generation.setNextEventSequence(sequence + 1);

        GenerationEventEntity event = new GenerationEventEntity();
        event.setId(UUID.randomUUID());
        event.setGenerationId(generation.getId());
        event.setSequenceNo(sequence);
        event.setEventType(eventType);
        event.setPayload(Objects.requireNonNull(payload, "payload is required"));
        event.setCreatedAt(createdAt);
        event.setExpiresAt(expiresAt);
        return event;
    }
}
