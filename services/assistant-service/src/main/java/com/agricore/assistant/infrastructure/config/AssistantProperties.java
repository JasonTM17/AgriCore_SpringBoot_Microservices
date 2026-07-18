package com.agricore.assistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agricore.assistant")
public record AssistantProperties(
        String provider,
        String openaiBaseUrl,
        String openaiApiKey,
        String openaiModel,
        int maxOutputTokens
) {
    public boolean generationAvailable() {
        String p = provider == null ? "none" : provider.trim().toLowerCase();
        if ("test".equals(p)) {
            return true;
        }
        if ("openai".equals(p) || "ollama".equals(p)) {
            return openaiApiKey != null && !openaiApiKey.isBlank();
        }
        return false;
    }

    public String normalizedProvider() {
        return provider == null || provider.isBlank() ? "none" : provider.trim().toLowerCase();
    }
}
