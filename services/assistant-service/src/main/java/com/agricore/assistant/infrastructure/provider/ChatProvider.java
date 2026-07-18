package com.agricore.assistant.infrastructure.provider;

import java.util.List;
import java.util.function.Consumer;

public interface ChatProvider {
    boolean available();

    String name();

    /**
     * Generate assistant text. Emits token deltas to {@code onDelta}; returns full text.
     * Must refuse tool-like unsafe instructions with a clear refusal message.
     */
    String generate(List<ChatMessage> history, String userPrompt, Consumer<String> onDelta);

    record ChatMessage(String role, String content) {
    }
}
