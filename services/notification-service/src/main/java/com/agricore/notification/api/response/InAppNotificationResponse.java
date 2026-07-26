package com.agricore.notification.api.response;

import java.time.Instant;
import java.util.UUID;

public record InAppNotificationResponse(
        UUID notificationId,
        String recipient,
        String subject,
        String body,
        Instant deliveredAt,
        Instant readAt
) {
}
