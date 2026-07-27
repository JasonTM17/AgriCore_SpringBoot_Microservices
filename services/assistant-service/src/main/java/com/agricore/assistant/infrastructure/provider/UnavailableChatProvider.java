package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties.ProviderType;
import reactor.core.publisher.Flux;

import java.util.Objects;

public class UnavailableChatProvider implements ChatProvider {

    private final ProviderType configuredProvider;
    private final String reasonCode;

    public UnavailableChatProvider(ProviderType configuredProvider) {
        this(configuredProvider, configuredProvider == ProviderType.NONE
                ? "AI_PROVIDER_UNAVAILABLE"
                : "AI_PROVIDER_ADAPTER_UNAVAILABLE");
    }

    private UnavailableChatProvider(ProviderType configuredProvider, String reasonCode) {
        this.configuredProvider = Objects.requireNonNull(configuredProvider, "configuredProvider is required");
        this.reasonCode = reasonCode;
    }

    public static UnavailableChatProvider misconfigured(ProviderType configuredProvider) {
        return new UnavailableChatProvider(configuredProvider, "AI_PROVIDER_CONFIGURATION_MISSING");
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(
                configuredProvider.externalName(),
                false,
                false,
                reasonCode
        );
    }

    @Override
    public Flux<ChatChunk> stream(ChatGenerationRequest request) {
        return Flux.error(AssistantProviderException.unavailable());
    }
}
