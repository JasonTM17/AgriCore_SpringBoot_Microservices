package com.agricore.assistant.application.model;

public record ProviderCapabilities(
        String provider,
        boolean available,
        boolean streaming,
        String reasonCode
) {
}
