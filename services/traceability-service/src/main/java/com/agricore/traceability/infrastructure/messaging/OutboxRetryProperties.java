package com.agricore.traceability.infrastructure.messaging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "agricore.outbox.publisher.retry")
@Validated
public record OutboxRetryProperties(
        @Min(100) @Max(86_400_000) long baseDelayMs,
        @Min(100) @Max(86_400_000) long maxDelayMs,
        @Min(1) @Max(100) int maxAttempts,
        boolean writeStateEnabled
) {

    public OutboxRetryProperties(long baseDelayMs, long maxDelayMs, int maxAttempts) {
        this(baseDelayMs, maxDelayMs, maxAttempts, true);
    }

    @ConstructorBinding
    public OutboxRetryProperties {
        if (baseDelayMs > maxDelayMs) {
            throw new IllegalArgumentException("Outbox retry base delay must not exceed max delay");
        }
    }

    public long delayForFailure(int failedAttempt) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("Failed attempt must be positive");
        }
        long delay = baseDelayMs;
        for (int attempt = 1; attempt < failedAttempt && delay < maxDelayMs; attempt++) {
            delay = Math.min(maxDelayMs, delay > maxDelayMs / 2 ? maxDelayMs : delay * 2);
        }
        return delay;
    }
}
