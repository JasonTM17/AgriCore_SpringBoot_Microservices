package com.agricore.assistant.infrastructure.persistence.entity;

import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "farm_id")
    private UUID farmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 16)
    private ConversationContextType contextType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "role_snapshot", nullable = false, columnDefinition = "TEXT")
    private String roleSnapshot;

    @Column(name = "next_message_sequence", nullable = false)
    private long nextMessageSequence;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    public ConversationEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }
    public ConversationContextType getContextType() { return contextType; }
    public void setContextType(ConversationContextType contextType) { this.contextType = contextType; }
    public ConversationStatus getStatus() { return status; }
    public void setStatus(ConversationStatus status) { this.status = status; }
    public String getRoleSnapshot() { return roleSnapshot; }
    public void setRoleSnapshot(String roleSnapshot) { this.roleSnapshot = roleSnapshot; }
    public long getNextMessageSequence() { return nextMessageSequence; }
    public void setNextMessageSequence(long nextMessageSequence) { this.nextMessageSequence = nextMessageSequence; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public void setPurgeAfter(Instant purgeAfter) { this.purgeAfter = purgeAfter; }
}
