package com.agricore.assistant.application.model;

import java.util.List;

public record ChatGenerationRequest(
        List<ChatTurn> turns,
        String model,
        int maxOutputTokens,
        double temperature
) {

    private static final int MAX_TURNS = 100;
    private static final int MAX_TOTAL_CONTENT_LENGTH = 200_000;
    private static final int MAX_MODEL_LENGTH = 200;
    private static final int MAX_OUTPUT_TOKENS = 8_192;

    public ChatGenerationRequest {
        turns = turns == null ? List.of() : List.copyOf(turns);
        if (turns.isEmpty() || turns.size() > MAX_TURNS) {
            throw new IllegalArgumentException("chat request must contain between 1 and 100 turns");
        }
        int totalContentLength = turns.stream()
                .mapToInt(turn -> turn.content().length())
                .sum();
        if (totalContentLength > MAX_TOTAL_CONTENT_LENGTH) {
            throw new IllegalArgumentException("chat request content must be at most 200000 characters");
        }
        model = model == null ? "" : model.strip();
        if (model.isEmpty() || model.length() > MAX_MODEL_LENGTH) {
            throw new IllegalArgumentException("model must be non-blank and at most 200 characters");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > MAX_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("maxOutputTokens must be between 1 and 8192");
        }
        if (Double.isNaN(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
    }
}
