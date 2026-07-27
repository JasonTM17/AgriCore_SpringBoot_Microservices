package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantToolPropertiesTest {

    @Test
    void acceptsOnlyExplicitlyAllowlistedBoundedFarmEndpoints() {
        AssistantToolProperties properties = new AssistantToolProperties();
        properties.setFarmBaseUrl("https://farm.internal:8443");
        properties.setAllowedHosts(Set.of("farm.internal"));
        properties.setConnectTimeout(Duration.ofSeconds(4));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setMaxResponseBytes(65_536);
        properties.setMaxPlots(12);

        assertThat(properties.validatedFarmBaseUri().toString()).isEqualTo("https://farm.internal:8443");
        assertThat(properties.validatedConnectTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.validatedReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.validatedMaxResponseBytes()).isEqualTo(65_536);
        assertThat(properties.validatedMaxPlots()).isEqualTo(12);
    }

    @Test
    void rejectsSsrFPathsCredentialsAndUnboundedControls() {
        assertInvalidUrl("https://attacker.example");
        assertInvalidUrl("https://user:pass@farm-service");
        assertInvalidUrl("https://farm-service/internal");
        assertInvalidUrl("https://farm-service?redirect=attacker.example");
        assertInvalidUrl("file:///etc/passwd");
        assertInvalidUrl("http://farm-service");

        AssistantToolProperties properties = validProperties();
        properties.setMaxPlots(21);
        assertThatThrownBy(properties::validatedMaxPlots).isInstanceOf(IllegalArgumentException.class);
        properties = validProperties();
        properties.setMaxResponseBytes(131_073);
        assertThatThrownBy(properties::validatedMaxResponseBytes).isInstanceOf(IllegalArgumentException.class);
        properties = validProperties();
        properties.setReadTimeout(Duration.ofSeconds(11));
        assertThatThrownBy(properties::validatedReadTimeout).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidUrl(String url) {
        AssistantToolProperties properties = validProperties();
        properties.setFarmBaseUrl(url);
        assertThatThrownBy(properties::validatedFarmBaseUri)
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AssistantToolProperties validProperties() {
        AssistantToolProperties properties = new AssistantToolProperties();
        properties.setFarmBaseUrl("https://farm-service");
        return properties;
    }
}
