package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives real {@link LoginRateLimiter#allow} with a failing Redis dependency.
 * Proves fail-closed (default production) vs fail-open policy.
 */
class LoginRateLimiterTest {

    private static SecurityProperties props(boolean rateLimitFailOpen) {
        return new SecurityProperties(
                "https://agricore.test/identity",
                "agricore-api",
                300L,
                3600L,
                4,
                5,
                15,
                20,
                "",
                "",
                true,
                rateLimitFailOpen,
                "agricore_refresh",
                "/api/v1/auth/web",
                false,
                "Strict",
                "http://localhost:5173"
        );
    }

    @Test
    void allow_returnsFalse_whenRedisThrows_andFailClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.1")).isFalse();
    }

    @Test
    void allow_returnsTrue_whenRedisThrows_andFailOpenEnabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(true));

        assertThat(limiter.allow("10.0.0.1")).isTrue();
    }

    @Test
    void allow_returnsTrue_whenCountWithinLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(3L);

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.2")).isTrue();
    }

    @Test
    void allow_returnsFalse_whenCountExceedsLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(21L);

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.3")).isFalse();
    }
}
