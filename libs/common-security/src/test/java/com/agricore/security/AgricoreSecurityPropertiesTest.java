package com.agricore.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mostly a binding holder, with one piece of real logic: {@code jwks-uri} is a legacy alias for
 * {@code jwk-set-uri}, and it guards against overwriting a good value with an empty one.
 */
class AgricoreSecurityPropertiesTest {

    @Test
    void devModeIsOffUnlessAskedFor() {
        assertThat(new AgricoreSecurityProperties().isDevMode())
                .as("the dev header filter must be inert by default")
                .isFalse();
    }

    @Test
    void legacyAliasSetsThePreferredProperty() {
        AgricoreSecurityProperties properties = new AgricoreSecurityProperties();
        properties.setJwksUri("https://identity.agricore.local/.well-known/jwks.json");

        assertThat(properties.getJwkSetUri())
                .isEqualTo("https://identity.agricore.local/.well-known/jwks.json");
    }

    /**
     * A service that sets only {@code jwk-set-uri} still gets an empty {@code jwks-uri} bound over
     * it by relaxed binding if the key is present but blank. Ignoring the blank keeps the real value
     * — the alternative is a decoder pointed at an empty URI, which fails at first verification
     * rather than at startup.
     */
    @Test
    void legacyAliasIgnoresBlankAndNull() {
        AgricoreSecurityProperties properties = new AgricoreSecurityProperties();
        properties.setJwkSetUri("https://identity.agricore.local/.well-known/jwks.json");

        properties.setJwksUri("   ");
        properties.setJwksUri(null);

        assertThat(properties.getJwkSetUri())
                .isEqualTo("https://identity.agricore.local/.well-known/jwks.json");
    }

    /**
     * Defaults point at a local identity service, so a misconfigured deployment fails to verify
     * rather than accepting tokens from an unexpected issuer.
     */
    @Test
    void shipsLocalDefaultsForIssuerAndAudience() {
        AgricoreSecurityProperties properties = new AgricoreSecurityProperties();

        assertThat(properties.getIssuer()).isEqualTo("https://agricore.local/identity");
        assertThat(properties.getAudience()).isEqualTo("agricore-api");
        assertThat(properties.getJwkSetUri()).startsWith("http://localhost:");
    }
}
