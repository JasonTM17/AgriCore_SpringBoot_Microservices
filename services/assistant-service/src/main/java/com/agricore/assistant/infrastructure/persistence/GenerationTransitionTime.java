package com.agricore.assistant.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;

final class GenerationTransitionTime {

    private GenerationTransitionTime() {
    }

    static void requireEventWindow(Instant occurredAt, Instant eventExpiresAt) {
        if (occurredAt == null || eventExpiresAt == null || !eventExpiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("event expiry must follow transition time");
        }
    }

    static long elapsedMillis(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("elapsed time endpoints are required");
        }
        return Math.max(0, Duration.between(startedAt, endedAt).toMillis());
    }

    static Long elapsedMillisIfStarted(Instant startedAt, Instant endedAt) {
        return startedAt == null ? null : elapsedMillis(startedAt, endedAt);
    }
}
