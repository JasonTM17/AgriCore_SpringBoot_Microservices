package com.agricore.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    private String channel;
    @Column(nullable = false, length = 320)
    private String recipient;
    @Column(nullable = false, length = 300)
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    @Column(name = "source_event_id")
    private UUID sourceEventId;
    @Column(name = "source_event_type", length = 150)
    private String sourceEventType;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "failure_retryable")
    private Boolean failureRetryable;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "failed_at")
    private Instant failedAt;
    @Column(name = "delivery_started_at")
    private Instant deliveryStartedAt;
    @Column(name = "delivery_claim_id")
    private UUID deliveryClaimId;
    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    public String getSourceEventType() { return sourceEventType; }
    public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Boolean getFailureRetryable() { return failureRetryable; }
    public void setFailureRetryable(Boolean failureRetryable) { this.failureRetryable = failureRetryable; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public Instant getDeliveryStartedAt() { return deliveryStartedAt; }
    public void setDeliveryStartedAt(Instant deliveryStartedAt) { this.deliveryStartedAt = deliveryStartedAt; }
    public UUID getDeliveryClaimId() { return deliveryClaimId; }
    public void setDeliveryClaimId(UUID deliveryClaimId) { this.deliveryClaimId = deliveryClaimId; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
    public void setDeliveryAttempts(int deliveryAttempts) { this.deliveryAttempts = deliveryAttempts; }
}
