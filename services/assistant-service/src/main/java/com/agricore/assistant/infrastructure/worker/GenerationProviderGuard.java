package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class GenerationProviderGuard {

    private final ChatProvider chatProvider;

    public GenerationProviderGuard(ChatProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    public void verify(GenerationExecutionContext context) {
        ProviderCapabilities capabilities = chatProvider.capabilities();
        if (capabilities == null || !capabilities.available() || !capabilities.streaming()) {
            throw GenerationProcessingException.failed(unavailableCode(capabilities));
        }
        if (!Objects.equals(capabilities.provider(), context.generation().provider())) {
            throw GenerationProcessingException.failed("AI_PROVIDER_CONFIGURATION_CHANGED");
        }
    }

    private static String unavailableCode(ProviderCapabilities capabilities) {
        if (capabilities == null || capabilities.reasonCode() == null) {
            return "AI_PROVIDER_UNAVAILABLE";
        }
        return switch (capabilities.reasonCode()) {
            case "AI_PROVIDER_CONFIGURATION_MISSING",
                 "AI_PROVIDER_ADAPTER_UNAVAILABLE",
                 "AI_PROVIDER_CIRCUIT_OPEN" -> capabilities.reasonCode();
            default -> "AI_PROVIDER_UNAVAILABLE";
        };
    }
}
