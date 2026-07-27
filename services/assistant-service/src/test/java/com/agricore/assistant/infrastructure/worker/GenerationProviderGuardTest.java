package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.domain.model.AssistantGeneration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationProviderGuardTest {

    private final ChatProvider provider = mock(ChatProvider.class);
    private final AssistantGeneration generation = mock(AssistantGeneration.class);
    private final GenerationExecutionContext context = new GenerationExecutionContext(generation, List.of());
    private final GenerationProviderGuard guard = new GenerationProviderGuard(provider);

    @Test
    void acceptsOnlyTheAvailableStreamingProviderCapturedAtSubmission() {
        when(generation.provider()).thenReturn("openai");
        when(provider.capabilities()).thenReturn(new ProviderCapabilities("openai", true, true, null));

        assertThatCode(() -> guard.verify(context)).doesNotThrowAnyException();

        when(provider.capabilities()).thenReturn(new ProviderCapabilities("ollama", true, true, null));
        assertFailureCode("AI_PROVIDER_CONFIGURATION_CHANGED");
    }

    @Test
    void exposesOnlyAllowlistedUnavailabilityCodes() {
        when(provider.capabilities()).thenReturn(
                new ProviderCapabilities("openai", false, false, "raw-provider-secret"));
        assertFailureCode("AI_PROVIDER_UNAVAILABLE");

        when(provider.capabilities()).thenReturn(
                new ProviderCapabilities("openai", false, true, "AI_PROVIDER_CIRCUIT_OPEN"));
        assertFailureCode("AI_PROVIDER_CIRCUIT_OPEN");
    }

    private void assertFailureCode(String expectedCode) {
        assertThatThrownBy(() -> guard.verify(context))
                .isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo(expectedCode);
    }
}
