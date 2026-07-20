package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.worker")
public class AssistantGenerationWorkerProperties {

    private boolean enabled = true;
    private int concurrency = 4;
    private int queueCapacity = 512;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private int deltaBatchSize = 16;
    private Duration deltaFlushInterval = Duration.ofMillis(100);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = requireRange(concurrency, 1, 32, "concurrency");
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = requireRange(queueCapacity, 16, 10_000, "queueCapacity");
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = requireDurationAtMost(leaseDuration, Duration.ofMinutes(5), "leaseDuration");
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = requireDurationAtMost(
                heartbeatInterval, Duration.ofMinutes(1), "heartbeatInterval");
    }

    public int getDeltaBatchSize() {
        return deltaBatchSize;
    }

    public void setDeltaBatchSize(int deltaBatchSize) {
        this.deltaBatchSize = requireRange(deltaBatchSize, 1, 64, "deltaBatchSize");
    }

    public Duration getDeltaFlushInterval() {
        return deltaFlushInterval;
    }

    public void setDeltaFlushInterval(Duration deltaFlushInterval) {
        this.deltaFlushInterval = requireDurationAtMost(
                deltaFlushInterval, Duration.ofSeconds(1), "deltaFlushInterval");
    }

    public void validate() {
        if (heartbeatInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException("heartbeatInterval must be shorter than leaseDuration");
        }
    }

    private static Duration requireDurationAtMost(Duration value, Duration maximum, String fieldName) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(fieldName + " must be positive and at most " + maximum);
        }
        return value;
    }

    private static int requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
