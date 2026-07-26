package com.agricore.identity.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        /**
         * When true, read the client address from the last X-Forwarded-For hop — the one the
         * trusted gateway appended. Earlier hops are caller-supplied and must not be trusted.
         */
        boolean trustForwardedHeaders
) {
}
