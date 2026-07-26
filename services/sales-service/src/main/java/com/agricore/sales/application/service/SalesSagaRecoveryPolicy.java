package com.agricore.sales.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class SalesSagaRecoveryPolicy {

    private static final Duration MAX_LEASE = Duration.ofMinutes(10);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final int maxAttempts;
    private final int batchSize;
    private final Duration executionLease;
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public SalesSagaRecoveryPolicy(
            @Value("${agricore.saga.recovery.max-attempts:5}") int maxAttempts,
            @Value("${agricore.saga.recovery.batch-size:50}") int batchSize,
            @Value("${agricore.saga.recovery.execution-lease:PT30S}") Duration executionLease,
            @Value("${agricore.saga.recovery.initial-delay:PT5S}") Duration initialDelay,
            @Value("${agricore.saga.recovery.maximum-delay:PT1M}") Duration maximumDelay
    ) {
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("saga recovery max-attempts must be between 1 and 20");
        }
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("saga recovery batch-size must be between 1 and 500");
        }
        requireDuration("execution-lease", executionLease, Duration.ofSeconds(10), MAX_LEASE);
        requireDuration("initial-delay", initialDelay, Duration.ofMillis(100), MAX_BACKOFF);
        requireDuration("maximum-delay", maximumDelay, initialDelay, MAX_BACKOFF);
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.executionLease = executionLease;
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
    }

    public Instant staleBefore(Instant now) {
        return now.minus(executionLease);
    }

    public Instant nextAttemptAt(int attempts, Instant now) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        long multiplier = 1L << exponent;
        Duration delay;
        try {
            delay = initialDelay.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            delay = maximumDelay;
        }
        if (delay.compareTo(maximumDelay) > 0) {
            delay = maximumDelay;
        }
        return now.plus(delay);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int batchSize() {
        return batchSize;
    }

    private static void requireDuration(
            String property,
            Duration value,
            Duration minimum,
            Duration maximum
    ) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "saga recovery " + property + " must be between " + minimum + " and " + maximum
            );
        }
    }
}
