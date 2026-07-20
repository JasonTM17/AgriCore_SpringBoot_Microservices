package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties.ProviderType;
import reactor.core.publisher.Flux;

public class UnavailableChatProvider implements ChatProvider {

    private final ProviderType configuredProvider;

    public UnavailableChatProvider(ProviderType configuredProvider) {
        this.configuredProvider = configuredProvider;
    }

    @Override
    public ProviderCapabilities capabilities() {
        String reasonCode = configuredProvider == ProviderType.NONE
                ? "AI_PROVIDER_UNAVAILABLE"
                : "AI_PROVIDER_ADAPTER_UNAVAILABLE";
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
