package com.agricore.assistant.application.model;

import java.time.Instant;
import java.util.UUID;

/** Values captured in one transaction before any provider work starts. */
public record GenerationSubmissionCommand(
        UUID conversationId,
        UUID ownerUserId,
        String idempotencyKey,
        String requestHash,
        String prompt,
        ToolEvidenceSnapshot toolEvidence,
        String provider,
        String model,
        Instant now,
        Instant purgeAfter,
        Instant eventExpiresAt
) {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_PROMPT_LENGTH = 200_000;

    public GenerationSubmissionCommand {
        if (conversationId == null || ownerUserId == null) {
            throw new IllegalArgumentException("conversationId and ownerUserId are required");
        }
        idempotencyKey = normalizeRequired(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
        requestHash = normalizeRequired(requestHash, "requestHash", 64);
        if (!requestHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 hex value");
        }
        prompt = normalizeRequired(prompt, "prompt", MAX_PROMPT_LENGTH);
        toolEvidence = toolEvidence == null ? ToolEvidenceSnapshot.empty() : toolEvidence;
        provider = normalizeRequired(provider, "provider", 32);
        model = model == null || model.isBlank() ? null : normalizeRequired(model, "model", 128);
        if (now == null || eventExpiresAt == null) {
            throw new IllegalArgumentException("timestamps are required");
        }
        if (eventExpiresAt.isBefore(now)) {
            throw new IllegalArgumentException("eventExpiresAt must not precede now");
        }
    }

    private static String normalizeRequired(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }
}
