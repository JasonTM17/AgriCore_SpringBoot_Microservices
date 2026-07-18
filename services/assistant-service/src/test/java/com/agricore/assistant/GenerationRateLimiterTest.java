package com.agricore.assistant;

import com.agricore.assistant.infrastructure.security.GenerationRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationRateLimiterTest {

    @Test
    void allow_permitsUpToLimit_thenDenies() {
        GenerationRateLimiter limiter = new GenerationRateLimiter(3);
        UUID owner = UUID.randomUUID();

        assertThat(limiter.allow(owner)).isTrue();
        assertThat(limiter.allow(owner)).isTrue();
        assertThat(limiter.allow(owner)).isTrue();
        assertThat(limiter.allow(owner)).isFalse();
    }

    @Test
    void allow_isIsolatedPerOwner() {
        GenerationRateLimiter limiter = new GenerationRateLimiter(1);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(limiter.allow(a)).isTrue();
        assertThat(limiter.allow(a)).isFalse();
        assertThat(limiter.allow(b)).isTrue();
    }
}
