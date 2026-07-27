package com.agricore.assistant.application.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record ChatChunk(
        String text,
        boolean terminal,
        String finishReason,
        Integer inputTokens,
        Integer outputTokens
) {
    private static final int MAX_DELTA_LENGTH = 65_536;
    private static final Pattern SAFE_FINISH_REASON = Pattern.compile("[A-Za-z0-9._-]+");

    public ChatChunk {
        text = text == null ? "" : text;
        finishReason = normalizeFinishReason(finishReason);
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        if (terminal) {
            if (!text.isEmpty()) {
                throw new IllegalArgumentException("terminal chat chunk must not contain text");
            }
            finishReason = finishReason == null ? "unknown" : finishReason;
        } else {
            if (text.isEmpty()) {
                throw new IllegalArgumentException("chat delta must contain text");
            }
            if (text.length() > MAX_DELTA_LENGTH) {
                throw new IllegalArgumentException("chat delta must be at most 65536 characters");
            }
            if (finishReason != null || inputTokens != null || outputTokens != null) {
                throw new IllegalArgumentException("chat delta must not contain terminal metadata");
            }
        }
    }

    public static ChatChunk delta(String text) {
        return new ChatChunk(text, false, null, null, null);
    }

    public static ChatChunk terminal(String finishReason, Integer inputTokens, Integer outputTokens) {
        return new ChatChunk("", true, finishReason, inputTokens, outputTokens);
    }

    private static void requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static String normalizeFinishReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100 || !SAFE_FINISH_REASON.matcher(normalized).matches()) {
            return "unknown";
        }
        return normalized;
    }
}
