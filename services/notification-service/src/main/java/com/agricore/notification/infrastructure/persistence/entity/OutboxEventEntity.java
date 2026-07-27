package com.agricore.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(nullable = false)
    private String topic;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "quarantined_at")
    private Instant quarantinedAt;

    public static OutboxEventEntity create(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String payload
    ) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.topic = topic;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getQuarantinedAt() { return quarantinedAt; }

    public void markPublished() {
        publishAttempts++;
        publishedAt = Instant.now();
        nextAttemptAt = null;
        quarantinedAt = null;
        lastError = null;
    }

    public boolean isEligibleForPublish(Instant now) {
        return publishedAt == null
                && quarantinedAt == null
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    public void markFailed(String error, Instant failedAt, long retryDelayMillis, int maxAttempts) {
        publishAttempts++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
        if (publishAttempts >= maxAttempts) {
            quarantinedAt = failedAt;
            nextAttemptAt = null;
        } else {
            quarantinedAt = null;
            nextAttemptAt = failedAt.plusMillis(retryDelayMillis);
        }
    }

    public void markFailedWithoutRetryState(String error) {
        publishAttempts++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
        nextAttemptAt = null;
        quarantinedAt = null;
    }
}
