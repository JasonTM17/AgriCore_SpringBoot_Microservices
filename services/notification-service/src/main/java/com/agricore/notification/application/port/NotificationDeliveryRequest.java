package com.agricore.notification.application.port;

public record NotificationDeliveryRequest(
        String channel,
        String recipient,
        String subject,
        String body
) {
}
