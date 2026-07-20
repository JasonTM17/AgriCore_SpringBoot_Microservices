package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantProviderPropertiesTest {

    @Test
    void bindsProviderSettingsWithBoundedOperationalControls() {
        AssistantProviderProperties properties = bind(Map.of(
                "agricore.assistant.provider.type", "openai",
                "agricore.assistant.provider.model", "gpt-4.1-mini",
                "agricore.assistant.provider.api-key", "secret-value",
                "agricore.assistant.provider.base-url", "https://llm.example.test",
                "agricore.assistant.provider.connect-timeout", "PT3S",
                "agricore.assistant.provider.read-timeout", "PT45S",
                "agricore.assistant.provider.max-generation-duration", "PT2M",
                "agricore.assistant.provider.max-input-characters", "50000",
                "agricore.assistant.provider.max-output-tokens", "2048",
                "agricore.assistant.provider.temperature", "0.4"
        ));

        assertThat(properties.getType()).isEqualTo(AssistantProviderProperties.ProviderType.OPENAI);
        assertThat(properties.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(properties.getApiKey()).isEqualTo("secret-value");
        assertThat(properties.getBaseUrl()).isEqualTo(URI.create("https://llm.example.test"));
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.getMaxGenerationDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getCircuitOpenDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getMaxInputCharacters()).isEqualTo(50_000);
        assertThat(properties.getMaxOutputTokens()).isEqualTo(2_048);
        assertThat(properties.getCircuitFailureThreshold()).isEqualTo(5);
        assertThat(properties.getTemperature()).isEqualTo(0.4);
    }

    @Test
    void keepsNoProviderDefaultsSafeAndCredentialFree() {
        AssistantProviderProperties properties = bind(Map.of());

        assertThat(properties.getType()).isEqualTo(AssistantProviderProperties.ProviderType.NONE);
        assertThat(properties.getModel()).isEmpty();
        assertThat(properties.getApiKey()).isEmpty();
        assertThat(properties.getBaseUrl()).isNull();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getMaxGenerationDuration()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.getCircuitOpenDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getMaxInputCharacters()).isEqualTo(40_000);
        assertThat(properties.getMaxOutputTokens()).isEqualTo(1_024);
        assertThat(properties.getCircuitFailureThreshold()).isEqualTo(5);
        assertThat(properties.getTemperature()).isEqualTo(0.2);
    }

    @Test
    void rejectsUnsafeUrlsAndOutOfRangeLimitsDuringBinding() {
        assertBindingFails("agricore.assistant.provider.base-url", "https://user:pass@llm.example.test");
        assertBindingFails("agricore.assistant.provider.base-url", "https://llm.example.test?key=value");
        assertBindingFails("agricore.assistant.provider.base-url", "file:///tmp/provider");
        assertBindingFails("agricore.assistant.provider.connect-timeout", "PT31S");
        assertBindingFails("agricore.assistant.provider.read-timeout", "PT6M");
        assertBindingFails("agricore.assistant.provider.max-generation-duration", "PT11M");
        assertBindingFails("agricore.assistant.provider.circuit-open-duration", "PT11M");
        assertBindingFails("agricore.assistant.provider.max-input-characters", "200001");
        assertBindingFails("agricore.assistant.provider.max-output-tokens", "8193");
        assertBindingFails("agricore.assistant.provider.circuit-failure-threshold", "101");
        assertBindingFails("agricore.assistant.provider.temperature", "2.1");
    }

    private AssistantProviderProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("agricore.assistant.provider", Bindable.of(AssistantProviderProperties.class))
                .orElseGet(AssistantProviderProperties::new);
    }

    private void assertBindingFails(String key, String value) {
        assertThatThrownBy(() -> bind(Map.of(key, value)))
                .isInstanceOf(BindException.class);
    }
}
