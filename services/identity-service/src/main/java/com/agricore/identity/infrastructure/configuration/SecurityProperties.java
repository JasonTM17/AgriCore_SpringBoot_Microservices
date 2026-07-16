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
        String publicKeyPath
) {
}
