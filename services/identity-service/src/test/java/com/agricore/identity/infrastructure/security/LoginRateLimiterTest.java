package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link LoginRateLimiter#allow} with a failing Redis dependency.
 * Proves fail-closed (default production) vs fail-open policy.
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> ops;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
    }

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
        when(ops.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.1")).isFalse();
    }

    @Test
    void allow_returnsTrue_whenRedisThrows_andFailOpenEnabled() {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(true));

        assertThat(limiter.allow("10.0.0.1")).isTrue();
    }

    @Test
    void allow_returnsTrue_whenCountWithinLimit() {
        when(ops.increment(anyString())).thenReturn(3L);

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.2")).isTrue();
    }

    @Test
    void allow_returnsFalse_whenCountExceedsLimit() {
        when(ops.increment(anyString())).thenReturn(21L);

        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.3")).isFalse();
    }

    @Test
    void allow_setsOneMinuteTtlForTheFirstIncrement() {
        when(ops.increment(anyString())).thenReturn(1L);
        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.4")).isTrue();

        verify(redis).expire("rl:login:10.0.0.4", Duration.ofMinutes(1));
    }

    @Test
    void allow_allowsNullRedisCountWithoutAttemptingToSetTtl() {
        when(ops.increment(anyString())).thenReturn((Long) null);
        LoginRateLimiter limiter = new LoginRateLimiter(redis, props(false));

        assertThat(limiter.allow("10.0.0.5")).isTrue();

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }
}
