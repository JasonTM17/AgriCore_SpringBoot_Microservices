package com.agricore.notification.application.port;

import java.util.UUID;

public record NotificationDeliveryRequest(
        UUID notificationId,
        UUID deliveryClaimId,
        String channel,
        String recipient,
        String subject,
        String body
) {
}
