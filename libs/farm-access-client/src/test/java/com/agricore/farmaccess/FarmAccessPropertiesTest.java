package com.agricore.farmaccess;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FarmAccessPropertiesTest {

    @Test
    void acceptsLoopbackHttpWithoutInsecureNetworkOptIn() {
        FarmAccessProperties properties = new FarmAccessProperties();

        assertThat(properties.validatedBaseUri()).isEqualTo(URI.create("http://localhost:8082"));
    }

    @Test
    void acceptsExplicitlyApprovedInternalHttpDestination() {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setBaseUrl("http://farm-service:8082");
        properties.setAllowInsecureHttp(true);

        assertThat(properties.validatedBaseUri()).isEqualTo(URI.create("http://farm-service:8082"));
    }

    @Test
    void acceptsExplicitDeploymentHostOverHttps() {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setBaseUrl("https://farm-service.agricore.svc.cluster.local");
        properties.setAllowedHosts(Set.of("farm-service.agricore.svc.cluster.local"));

        assertThat(properties.validatedBaseUri())
                .isEqualTo(URI.create("https://farm-service.agricore.svc.cluster.local"));
    }

    @Test
    void rejectsCredentialBearingOrPathBearingBaseUrls() {
        assertInvalidBaseUrl("https://user:secret@farm-service");
        assertInvalidBaseUrl("https://farm-service/internal");
        assertInvalidBaseUrl("https://farm-service?redirect=attacker.example");
        assertInvalidBaseUrl("https://farm-service#fragment");
    }

    @Test
    void rejectsNullNonPositiveAndExcessiveTimeouts() {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setConnectTimeout(null);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedConnectTimeout);

        properties.setConnectTimeout(Duration.ZERO);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedConnectTimeout);

        properties.setReadTimeout(Duration.ofSeconds(31));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedReadTimeout);
    }

    @Test
    void rejectsResponseLimitsOutsideBoundedRange() {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setMaxResponseBytes(255);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxResponseBytes);

        properties.setMaxResponseBytes(65_537);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxResponseBytes);
    }

    private static void assertInvalidBaseUrl(String baseUrl) {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setBaseUrl(baseUrl);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedBaseUri);
    }
}
