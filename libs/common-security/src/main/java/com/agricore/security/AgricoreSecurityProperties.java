package com.agricore.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agricore.security")
public class AgricoreSecurityProperties {

    /**
     * When true, accept X-Dev-User / X-Dev-Roles for local tests only.
     */
    private boolean devMode = false;

    /**
     * JWKS URI published by identity-service (preferred property: jwk-set-uri).
     */
    private String jwkSetUri = "http://localhost:8081/.well-known/jwks.json";

    /**
     * Expected JWT issuer claim.
     */
    private String issuer = "https://agricore.local/identity";

    /**
     * Expected JWT audience claim (identity issues {@code agricore-api}).
     */
    private String audience = "agricore-api";

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    /** Alias for legacy yml key agricore.security.jwks-uri */
    public void setJwksUri(String jwksUri) {
        if (jwksUri != null && !jwksUri.isBlank()) {
            this.jwkSetUri = jwksUri;
        }
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
