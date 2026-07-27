package com.agricore.notification.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import com.agricore.notification.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationEventOutboxWriter {

    private static final String TOPIC = "agricore.notification.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notificationRequested(NotificationEntity notification, String sourceEventType) {
        ObjectNode payload = commonPayload(notification, sourceEventType);
        payload.put("requestedAt", notification.getCreatedAt().toString());
        enqueue(EventTypes.NOTIFICATION_REQUESTED, notification, notification.getCreatedAt(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notificationSent(NotificationEntity notification, String sourceEventType) {
        ObjectNode payload = commonPayload(notification, sourceEventType);
        payload.put("notificationId", notification.getId().toString());
        payload.put("status", notification.getStatus());
        payload.put("deliveryAttempts", notification.getDeliveryAttempts());
        payload.put("sentAt", notification.getSentAt().toString());
        enqueue(EventTypes.NOTIFICATION_SENT, notification, notification.getSentAt(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notificationFailed(NotificationEntity notification, String sourceEventType) {
        ObjectNode payload = commonPayload(notification, sourceEventType);
        payload.put("status", notification.getStatus());
        payload.put("errorCode", notification.getErrorCode());
        payload.put("errorMessage", notification.getErrorMessage());
        payload.put("retryable", Boolean.TRUE.equals(notification.getFailureRetryable()));
        payload.put("deliveryAttempts", notification.getDeliveryAttempts());
        payload.put("failedAt", notification.getFailedAt().toString());
        enqueue(EventTypes.NOTIFICATION_FAILED, notification, notification.getFailedAt(), payload);
    }

    private ObjectNode commonPayload(NotificationEntity notification, String sourceEventType) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("notificationId", notification.getId().toString());
        if (notification.getSourceEventId() == null) {
            payload.putNull("sourceEventId");
        } else {
            payload.put("sourceEventId", notification.getSourceEventId().toString());
        }
        payload.put("sourceEventType", sourceEventType);
        payload.put("channel", notification.getChannel());
        payload.put("recipient", notification.getRecipient());
        payload.put("subject", notification.getSubject());
        payload.put("correlationId", notification.getCorrelationId());
        return payload;
    }

    private void enqueue(
            String eventType,
            NotificationEntity notification,
            Instant occurredAt,
            ObjectNode payload
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventType.endsWith(".v2") ? 2 : 1);
        envelope.put("occurredAt", occurredAt.toString());
        if (notification.getCorrelationId() != null) {
            envelope.put("correlationId", notification.getCorrelationId());
        }
        envelope.put("producer", "notification-service");
        envelope.set("payload", payload);
        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    "Notification",
                    notification.getId().toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize notification event " + eventType, exception);
        }
    }
}
