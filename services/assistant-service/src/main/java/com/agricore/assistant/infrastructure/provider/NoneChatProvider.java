package com.agricore.assistant.infrastructure.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
public class NoneChatProvider implements ChatProvider {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String name() {
        return "none";
    }

    @Override
    public String generate(List<ChatMessage> history, String userPrompt, Consumer<String> onDelta) {
        throw new UnsupportedOperationException("No chat provider configured");
    }
}
