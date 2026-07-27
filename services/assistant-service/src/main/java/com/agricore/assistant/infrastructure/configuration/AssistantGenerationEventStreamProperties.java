package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.streaming")
public class AssistantGenerationEventStreamProperties {

    private int maxConnections = 64;
    private int schedulerThreads = 2;
    private int batchSize = 100;
    private Duration pollInterval = Duration.ofMillis(750);
    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private Duration maxConnectionDuration = Duration.ofMinutes(5);

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = requireRange(maxConnections, 1, 1_000, "maxConnections");
    }

    public int getSchedulerThreads() {
        return schedulerThreads;
    }

    public void setSchedulerThreads(int schedulerThreads) {
        this.schedulerThreads = requireRange(schedulerThreads, 1, 16, "schedulerThreads");
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = requireRange(batchSize, 1, 1_000, "batchSize");
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = requireDurationBetween(
                pollInterval, Duration.ofMillis(100), Duration.ofSeconds(5), "pollInterval");
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = requireDurationBetween(
                heartbeatInterval, Duration.ofSeconds(1), Duration.ofMinutes(1), "heartbeatInterval");
    }

    public Duration getMaxConnectionDuration() {
        return maxConnectionDuration;
    }

    public void setMaxConnectionDuration(Duration maxConnectionDuration) {
        this.maxConnectionDuration = requireDurationBetween(
                maxConnectionDuration, Duration.ofSeconds(30), Duration.ofMinutes(30),
                "maxConnectionDuration");
    }

    public void validate() {
        if (heartbeatInterval.compareTo(pollInterval) < 0) {
            throw new IllegalArgumentException("heartbeatInterval must not be shorter than pollInterval");
        }
        if (maxConnectionDuration.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException("maxConnectionDuration must exceed heartbeatInterval");
        }
    }

    private static Duration requireDurationBetween(
            Duration value,
            Duration minimum,
            Duration maximum,
            String fieldName
    ) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minimum + " and " + maximum);
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
