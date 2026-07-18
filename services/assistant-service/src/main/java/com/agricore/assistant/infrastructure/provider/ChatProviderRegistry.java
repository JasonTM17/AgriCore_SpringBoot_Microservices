package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.infrastructure.config.AssistantProperties;
import org.springframework.stereotype.Component;

@Component
public class ChatProviderRegistry {

    private final AssistantProperties properties;
    private final NoneChatProvider noneChatProvider;
    private final TestChatProvider testChatProvider;

    public ChatProviderRegistry(
            AssistantProperties properties,
            NoneChatProvider noneChatProvider,
            TestChatProvider testChatProvider
    ) {
        this.properties = properties;
        this.noneChatProvider = noneChatProvider;
        this.testChatProvider = testChatProvider;
    }

    public ChatProvider active() {
        return switch (properties.normalizedProvider()) {
            case "test" -> testChatProvider;
            case "openai", "ollama" -> properties.generationAvailable() ? testChatProvider : noneChatProvider;
            // OpenAI-compatible HTTP adapter intentionally deferred; test provider stands in when key present for demos.
            default -> noneChatProvider;
        };
    }
}
