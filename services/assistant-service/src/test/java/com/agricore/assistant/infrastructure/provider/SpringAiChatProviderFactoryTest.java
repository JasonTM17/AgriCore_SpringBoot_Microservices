package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatProviderFactoryTest {

    private final SpringAiChatProviderFactory factory =
            new SpringAiChatProviderFactory(ObservationRegistry.NOOP);

    @Test
    void keepsDisabledProviderCredentialFree() {
        AssistantProviderProperties properties = new AssistantProviderProperties();

        ChatProvider provider = factory.create(properties);

        assertThat(provider.capabilities().provider()).isEqualTo("none");
        assertThat(provider.capabilities().reasonCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    }

    @Test
    void reportsMissingRequiredOpenAiConfigurationWithoutCrashingBoot() {
        AssistantProviderProperties properties = properties(AssistantProviderProperties.ProviderType.OPENAI);

        ChatProvider provider = factory.create(properties);

        assertThat(provider.capabilities().available()).isFalse();
        assertThat(provider.capabilities().reasonCode()).isEqualTo("AI_PROVIDER_CONFIGURATION_MISSING");
    }

    @Test
    void buildsOpenAiAndOllamaAdaptersOnlyWhenConfigurationIsComplete() {
        AssistantProviderProperties openAi = properties(AssistantProviderProperties.ProviderType.OPENAI);
        openAi.setApiKey("test-key");
        openAi.setBaseUrl(URI.create("https://llm.example.test"));
        ChatProvider openAiProvider = factory.create(openAi);

        AssistantProviderProperties ollama = properties(AssistantProviderProperties.ProviderType.OLLAMA);
        ChatProvider ollamaProvider = factory.create(ollama);

        assertThat(openAiProvider.capabilities()).satisfies(capabilities -> {
            assertThat(capabilities.provider()).isEqualTo("openai");
            assertThat(capabilities.available()).isTrue();
            assertThat(capabilities.streaming()).isTrue();
        });
        assertThat(ollamaProvider.capabilities()).satisfies(capabilities -> {
            assertThat(capabilities.provider()).isEqualTo("ollama");
            assertThat(capabilities.available()).isTrue();
            assertThat(capabilities.streaming()).isTrue();
        });
    }

    private AssistantProviderProperties properties(AssistantProviderProperties.ProviderType type) {
        AssistantProviderProperties properties = new AssistantProviderProperties();
        properties.setType(type);
        properties.setModel("test-model");
        return properties;
    }
}
