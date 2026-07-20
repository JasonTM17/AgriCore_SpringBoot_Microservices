package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnavailableChatProviderTest {

    @Test
    void distinguishesDisabledFromConfiguredButMissingAdapter() {
        assertThat(new UnavailableChatProvider(ProviderType.NONE).capabilities())
                .extracting("provider", "available", "streaming", "reasonCode")
                .containsExactly("none", false, false, "AI_PROVIDER_UNAVAILABLE");
        assertThat(new UnavailableChatProvider(ProviderType.OPENAI).capabilities())
                .extracting("provider", "available", "streaming", "reasonCode")
                .containsExactly("openai", false, false, "AI_PROVIDER_ADAPTER_UNAVAILABLE");
    }

    @Test
    void failsGenerationWithSafeStructuredProviderError() {
        UnavailableChatProvider provider = new UnavailableChatProvider(ProviderType.NONE);

        assertThatThrownBy(() -> provider.stream(request()).blockLast())
                .isInstanceOfSatisfying(AssistantProviderException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
                    assertThat(error.getMessage()).isEqualTo("The configured AI provider is unavailable");
                    assertThat(error.isRetryable()).isFalse();
                });
    }

    private ChatGenerationRequest request() {
        return new ChatGenerationRequest(
                List.of(new ChatTurn(ChatTurnRole.USER, "hello")),
                "test-model",
                128,
                0
        );
    }
}
