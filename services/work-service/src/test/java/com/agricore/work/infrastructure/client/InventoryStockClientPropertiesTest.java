package com.agricore.work.infrastructure.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InventoryStockClientPropertiesTest {

    @Test
    void acceptsLoopbackAndExplicitInternalDestinations() {
        InventoryStockClientProperties loopback = new InventoryStockClientProperties();
        assertThat(loopback.validatedBaseUri()).isEqualTo(URI.create("http://localhost:8086"));

        InventoryStockClientProperties internal = new InventoryStockClientProperties();
        internal.setBaseUrl("http://inventory-service:8086");
        internal.setAllowInsecureHttp(true);
        assertThat(internal.validatedBaseUri()).isEqualTo(URI.create("http://inventory-service:8086"));
    }

    @Test
    void rejectsUnapprovedOrCredentialBearingDestinations() {
        assertInvalid("https://attacker.example");
        assertInvalid("https://user:secret@inventory-service");
        assertInvalid("https://inventory-service/internal");
        assertInvalid("https://inventory-service?next=attacker.example");
    }

    @Test
    void rejectsUnsafeTimeoutAndResponseLimits() {
        InventoryStockClientProperties properties = new InventoryStockClientProperties();
        properties.setReadTimeout(Duration.ofSeconds(31));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedReadTimeout);

        properties.setMaxResponseBytes(65_537);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxResponseBytes);

        properties.setAllowedHosts(Set.of());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedBaseUri);
    }

    private static void assertInvalid(String baseUrl) {
        InventoryStockClientProperties properties = new InventoryStockClientProperties();
        properties.setBaseUrl(baseUrl);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedBaseUri);
    }
}
