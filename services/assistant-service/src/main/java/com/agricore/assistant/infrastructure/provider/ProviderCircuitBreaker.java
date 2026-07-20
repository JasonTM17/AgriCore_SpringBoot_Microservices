package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.port.AssistantProviderException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ProviderCircuitBreaker {

    private final int failureThreshold;
    private final long openDurationNanos;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAtNanos = new AtomicLong();

    public ProviderCircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        Objects.requireNonNull(openDuration, "openDuration is required");
        if (openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        long durationNanos;
        try {
            durationNanos = openDuration.toNanos();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("openDuration is too large", ex);
        }
        if (durationNanos <= 0) {
            throw new IllegalArgumentException("openDuration is too large");
        }
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = durationNanos;
    }

    public boolean tryAcquire() {
        while (true) {
            State current = state.get();
            if (current == State.CLOSED) {
                return true;
            }
            if (current == State.HALF_OPEN) {
                return false;
            }
            if (!cooldownElapsed()) {
                return false;
            }
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                return true;
            }
        }
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    public void onFailure(Throwable failure) {
        if (failure instanceof Error || failure instanceof CancellationException) {
            return;
        }
        if (failure instanceof AssistantProviderException providerFailure && !providerFailure.isRetryable()) {
            closeAfterNonRetryableProbe();
            return;
        }
        if (state.get() == State.HALF_OPEN) {
            open();
            return;
        }
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            open();
        }
    }

    public void onCancellation() {
        if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            openedAtNanos.set(System.nanoTime());
        }
    }

    public boolean isOpen() {
        return state.get() == State.OPEN && !cooldownElapsed();
    }

    private boolean cooldownElapsed() {
        return System.nanoTime() - openedAtNanos.get() >= openDurationNanos;
    }

    private void closeAfterNonRetryableProbe() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            consecutiveFailures.set(0);
        }
    }

    private void open() {
        openedAtNanos.set(System.nanoTime());
        state.set(State.OPEN);
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
