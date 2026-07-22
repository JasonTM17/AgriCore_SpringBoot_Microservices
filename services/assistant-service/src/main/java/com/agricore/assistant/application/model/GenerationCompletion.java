package com.agricore.assistant.application.model;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public record GenerationCompletion(
        String content,
        String finishReason,
        Integer inputTokens,
        Integer outputTokens,
        Instant firstTokenAt,
        Instant completedAt,
        Instant eventExpiresAt
) {
    private static final Pattern SAFE_FINISH_REASON = Pattern.compile("[A-Za-z0-9._-]+");

    public GenerationCompletion {
        if (content == null || content.isBlank() || content.length() > 200_000) {
            throw new IllegalArgumentException("assistant content is invalid");
        }
        finishReason = normalizeFinishReason(finishReason);
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        if (firstTokenAt == null || completedAt == null || firstTokenAt.isAfter(completedAt)
                || eventExpiresAt == null || !eventExpiresAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("completion timestamps are invalid");
        }
    }

    private static String normalizeFinishReason(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return normalized.length() <= 100 && SAFE_FINISH_REASON.matcher(normalized).matches()
                ? normalized
                : "unknown";
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
