package com.agricore.assistant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_audit_events")
public class AssistantAuditEventEntity {

    @Id
    private UUID id;
    @Column(name = "actor_subject", nullable = false)
    private UUID actorSubject;
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;
    @Column(name = "farm_id")
    private UUID farmId;
    @Column(name = "conversation_id")
    private UUID conversationId;
    @Column(name = "generation_id")
    private UUID generationId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(nullable = false, length = 16)
    private String outcome;
    @Column(name = "reason_code", length = 64)
    private String reasonCode;
    @Column(name = "trace_id", length = 128)
    private String traceId;
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    @Column(length = 2000)
    private String metadata;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "retain_until", nullable = false)
    private Instant retainUntil;

    public AssistantAuditEventEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getActorSubject() { return actorSubject; }
    public void setActorSubject(UUID actorSubject) { this.actorSubject = actorSubject; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public UUID getGenerationId() { return generationId; }
    public void setGenerationId(UUID generationId) { this.generationId = generationId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRetainUntil() { return retainUntil; }
    public void setRetainUntil(Instant retainUntil) { this.retainUntil = retainUntil; }
}
