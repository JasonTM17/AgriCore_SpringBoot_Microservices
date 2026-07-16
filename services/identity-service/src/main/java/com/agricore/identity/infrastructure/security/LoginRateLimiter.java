package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sliding fixed-window login rate limiter keyed by IP.
 * Falls back to allowing traffic if Redis is unavailable (logged by caller).
 */
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redis;
    private final SecurityProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, SecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean allow(String clientIp) {
        try {
            String key = "rl:login:" + clientIp;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            return count == null || count <= properties.loginRateLimitPerMinute();
        } catch (Exception ex) {
            // Fail-open for local/dev without Redis; production should monitor Redis health.
            return true;
        }
    }
}
