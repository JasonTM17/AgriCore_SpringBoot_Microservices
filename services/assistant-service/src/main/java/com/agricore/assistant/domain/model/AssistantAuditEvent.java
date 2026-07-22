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

    public static AssistantAuditEvent toolDecision(
            UUID actorSubject,
            UUID farmId,
            UUID conversationId,
            UUID generationId,
            String outcome,
            String reasonCode,
            String metadata,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, actorSubject, farmId, conversationId, generationId,
                "TOOL_EVIDENCE_DECISION", outcome, reasonCode, null, null, metadata,
                createdAt, retainUntil
        );
    }

    public static AssistantAuditEvent toolAttempt(
            UUID actorSubject,
            UUID farmId,
            UUID conversationId,
            String outcome,
            String reasonCode,
            String metadata,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, actorSubject, farmId, conversationId, null,
                "TOOL_EVIDENCE_ATTEMPT", outcome, reasonCode, null, null, metadata,
                createdAt, retainUntil
        );
    }

    public static AssistantAuditEvent submissionRejected(
            UUID actorSubject,
            UUID farmId,
            UUID conversationId,
            String outcome,
            String reasonCode,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, actorSubject, farmId, conversationId, null,
                "GENERATION_SUBMISSION_REJECTED", outcome, reasonCode, null, null, null,
                createdAt, retainUntil
        );
    }

    public static AssistantAuditEvent outputDecision(
            UUID actorSubject,
            UUID farmId,
            UUID conversationId,
            UUID generationId,
            String outcome,
            String reasonCode,
            Instant createdAt,
            Instant retainUntil
    ) {
        return new AssistantAuditEvent(
                UUID.randomUUID(), actorSubject, actorSubject, farmId, conversationId, generationId,
                "GENERATION_OUTPUT_DECISION", outcome, reasonCode, null, null, null,
                createdAt, retainUntil
        );
    }
}
