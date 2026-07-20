package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.application.port.ChatGenerationPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AssistantChatGenerationPolicy implements ChatGenerationPolicy {

    private final AssistantProviderProperties properties;

    public AssistantChatGenerationPolicy(AssistantProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public int maxInputCharacters() {
        return properties.getMaxInputCharacters();
    }

    @Override
    public int maxOutputTokens() {
        return properties.getMaxOutputTokens();
    }

    @Override
    public double temperature() {
        return properties.getTemperature();
    }

    @Override
    public Duration maxGenerationDuration() {
        return properties.getMaxGenerationDuration();
    }
}
