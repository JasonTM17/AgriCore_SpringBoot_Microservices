package com.agricore.assistant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_audit_events")
public class AssistantAuditEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "generation_id")
    private UUID generationId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public UUID getGenerationId() { return generationId; }
    public void setGenerationId(UUID generationId) { this.generationId = generationId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
