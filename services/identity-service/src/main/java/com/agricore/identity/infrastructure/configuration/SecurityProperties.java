package com.agricore.identity.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "agricore.security")
public record SecurityProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        int bcryptStrength,
        int maxFailedLogins,
        int lockoutDurationMinutes,
        int loginRateLimitPerMinute,
        String privateKeyPath,
        String publicKeyPath,
        /** When false, POST /register returns 403 (production default). */
        boolean registrationEnabled,
        /** When false, Redis errors deny login (fail-closed). Tests may set true. */
        boolean rateLimitFailOpen,
        /** HttpOnly refresh cookie name for browser auth endpoints. */
        String refreshCookieName,
        /** Narrow cookie path so the browser only attaches the credential to web auth routes. */
        String refreshCookiePath,
        /** Production should set true; local HTTP console may set false. */
        boolean refreshCookieSecure,
        /** Cookie SameSite policy; Strict by default for CSRF resistance. */
        String refreshCookieSameSite,
        /** Comma-separated browser origins allowed to call web cookie auth endpoints. */
        String webAllowedOrigins
) {
    public List<String> webAllowedOriginList() {
        if (webAllowedOrigins == null || webAllowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(webAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
