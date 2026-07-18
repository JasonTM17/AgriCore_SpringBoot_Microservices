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
        // Only the deterministic test provider is implemented for generation.
        // openai/ollama keys alone do not enable generation until real adapters ship.
        return "test".equals(p);
    }

    public String normalizedProvider() {
        return provider == null || provider.isBlank() ? "none" : provider.trim().toLowerCase();
    }
}
