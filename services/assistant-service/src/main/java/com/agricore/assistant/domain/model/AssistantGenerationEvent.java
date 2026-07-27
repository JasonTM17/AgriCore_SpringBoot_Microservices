package com.agricore.assistant.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AssistantGenerationEvent(
        UUID id,
        UUID generationId,
        long sequenceNo,
        GenerationEventType eventType,
        String payload,
        Instant createdAt,
        Instant expiresAt
) {
    private static final int MAX_PAYLOAD_LENGTH = 65_536;

    public AssistantGenerationEvent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (sequenceNo < 0) {
            throw new IllegalArgumentException("sequenceNo must not be negative");
        }
        payload = payload == null ? "" : payload;
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("event payload is too large");
        }
        if (expiresAt != null && expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt must not precede createdAt");
        }
    }
}
