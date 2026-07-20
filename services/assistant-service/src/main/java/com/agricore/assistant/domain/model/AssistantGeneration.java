package com.agricore.assistant.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AssistantGeneration(
        UUID id,
        UUID conversationId,
        UUID ownerUserId,
        UUID farmId,
        String idempotencyKey,
        String requestHash,
        GenerationStatus status,
        UUID activeConversationId,
        String errorCode,
        List<String> roleSnapshot,
        long nextEventSequence,
        String provider,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long firstTokenLatencyMs,
        Long providerLatencyMs,
        Long totalLatencyMs,
        Instant queuedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant firstTokenAt,
        Instant cancelRequestedAt,
        Instant cancelledAt,
        UUID leaseToken,
        Instant leaseExpiresAt,
        int attemptCount,
        long version,
        Instant completedAt,
        Instant purgeAfter
) {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_PROVIDER_LENGTH = 32;
    private static final int MAX_MODEL_LENGTH = 128;

    public AssistantGeneration {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(conversationId, "conversationId is required");
        Objects.requireNonNull(ownerUserId, "ownerUserId is required");
        Objects.requireNonNull(status, "status is required");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
        requestHash = requireText(requestHash, "requestHash", 64);
        if (!requestHash.matches("[0-9a-fA-F]{64}")
                && !requestHash.matches("legacy-[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 or legacy migration value");
        }
        roleSnapshot = roleSnapshot == null ? List.of() : List.copyOf(roleSnapshot);
        errorCode = errorCode == null || errorCode.isBlank() ? null : requireText(errorCode, "errorCode", 64);
        provider = requireText(provider, "provider", MAX_PROVIDER_LENGTH);
        model = model == null || model.isBlank() ? null : requireText(model, "model", MAX_MODEL_LENGTH);
        requireNonNegative(nextEventSequence, "nextEventSequence");
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(firstTokenLatencyMs, "firstTokenLatencyMs");
        requireNonNegative(providerLatencyMs, "providerLatencyMs");
        requireNonNegative(totalLatencyMs, "totalLatencyMs");
        requireNonNegative(attemptCount, "attemptCount");
        requireNonNegative(version, "version");
        if (status.active() && !conversationId.equals(activeConversationId)) {
            throw new IllegalArgumentException("active generation must own its conversation slot");
        }
        if (status.terminal() && activeConversationId != null) {
            throw new IllegalArgumentException("terminal generation must release its conversation slot");
        }
    }

    public boolean active() {
        return status.active();
    }

    public boolean terminal() {
        return status.terminal();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
