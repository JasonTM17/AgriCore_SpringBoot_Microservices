package com.agricore.notification.application.service;

import java.util.UUID;

public record NotificationEventCommand(
        UUID eventId,
        String eventType,
        String channel,
        String recipient,
        String subject,
        String body,
        String correlationId
) {
}
