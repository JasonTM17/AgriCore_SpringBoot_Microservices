package com.agricore.assistant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_events")
public class GenerationEventEntity {

    @Id
    private UUID id;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getGenerationId() { return generationId; }
    public void setGenerationId(UUID generationId) { this.generationId = generationId; }
    public long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(long sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
