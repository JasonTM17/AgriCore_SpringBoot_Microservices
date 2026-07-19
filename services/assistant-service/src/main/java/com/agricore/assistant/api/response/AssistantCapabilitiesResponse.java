package com.agricore.assistant.api.response;

import com.agricore.assistant.application.model.ProviderCapabilities;

public record AssistantCapabilitiesResponse(
        String provider,
        boolean available,
        boolean streaming,
        String reasonCode
) {
    public static AssistantCapabilitiesResponse from(ProviderCapabilities capabilities) {
        return new AssistantCapabilitiesResponse(
                capabilities.provider(),
                capabilities.available(),
                capabilities.streaming(),
                capabilities.reasonCode()
        );
    }
}
