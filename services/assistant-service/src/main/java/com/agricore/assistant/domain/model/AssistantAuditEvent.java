package com.agricore.assistant.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AssistantAuditEvent(
        UUID id,
        UUID actorSubject,
        UUID ownerUserId,
        UUID farmId,
        UUID conversationId,
        UUID generationId,
        String action,
        String outcome,
        String reasonCode,
        String traceId,
        String correlationId,
        String metadata,
        Instant createdAt,
        Instant retainUntil
) {
    public static AssistantAuditEvent success(
            UUID actorSubject,
            UUID ownerUserId,
            UUID farmId,
            UUID conversationId,
            String action,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, ownerUserId, farmId, conversationId, null,
                action, "SUCCESS", null, null, null, null, createdAt, retainUntil
        );
    }

    public static AssistantAuditEvent generationSuccess(
            UUID actorSubject,
            UUID ownerUserId,
            UUID farmId,
            UUID conversationId,
            UUID generationId,
            String action,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, ownerUserId, farmId, conversationId, generationId,
                action, "SUCCESS", null, null, null, null, createdAt, retainUntil
        );
    }
}
