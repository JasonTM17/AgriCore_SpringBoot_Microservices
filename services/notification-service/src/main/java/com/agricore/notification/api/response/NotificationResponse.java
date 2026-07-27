package com.agricore.notification.api.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String channel,
        String recipient,
        String subject,
        String status,
        String correlationId,
        Instant createdAt,
        Instant sentAt,
        Instant failedAt,
        String errorCode,
        String errorMessage,
        Boolean failureRetryable,
        int deliveryAttempts
) {
}
