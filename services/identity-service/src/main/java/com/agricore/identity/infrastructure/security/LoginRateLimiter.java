package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sliding fixed-window login rate limiter keyed by IP.
 * Production default is fail-closed when Redis is unavailable.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

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
            if (properties.rateLimitFailOpen()) {
                log.warn("Login rate limiter Redis error; fail-open enabled: {}", ex.getMessage());
                return true;
            }
            log.error("Login rate limiter Redis error; denying login (fail-closed): {}", ex.getMessage());
            return false;
        }
    }
}
