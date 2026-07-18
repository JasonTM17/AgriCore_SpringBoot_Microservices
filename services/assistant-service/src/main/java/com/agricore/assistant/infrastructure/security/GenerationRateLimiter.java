package com.agricore.assistant.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local fixed-window rate limit for new generation starts (H4 residual).
 * Not multi-instance safe — Redis-backed limiter is a follow-up for HA.
 */
@Component
public class GenerationRateLimiter {

    private final int limitPerMinute;
    private final ConcurrentHashMap<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();

    public GenerationRateLimiter() {
        this(30);
    }

    /** Package-visible for unit tests with a tight limit. */
    public GenerationRateLimiter(int limitPerMinute) {
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    /**
     * @return true if the caller may start a new generation
     */
    public boolean allow(UUID ownerUserId) {
        UUID key = ownerUserId == null ? new UUID(0L, 0L) : ownerUserId;
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = windows.computeIfAbsent(key, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limitPerMinute) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
