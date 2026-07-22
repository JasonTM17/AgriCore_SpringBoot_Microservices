package com.agricore.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static ProcessedEventEntity create(UUID eventId, String consumerName) {
        ProcessedEventEntity event = new ProcessedEventEntity();
        event.id = UUID.randomUUID();
        event.eventId = eventId;
        event.consumerName = consumerName;
        event.processedAt = Instant.now();
        return event;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getConsumerName() { return consumerName; }
    public Instant getProcessedAt() { return processedAt; }
}
