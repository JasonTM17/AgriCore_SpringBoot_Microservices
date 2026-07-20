package com.agricore.assistant.application.model;

public record ChatChunk(
        String text,
        boolean terminal,
        String finishReason,
        Integer inputTokens,
        Integer outputTokens
) {
    public ChatChunk {
        text = text == null ? "" : text;
        finishReason = finishReason == null || finishReason.isBlank() ? null : finishReason.strip();
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
}
