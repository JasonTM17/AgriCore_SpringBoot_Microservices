package com.agricore.notification.application.service;

import java.util.UUID;

record NotificationDraft(
        String channel,
        String recipient,
        String subject,
        String body,
        String correlationId,
        UUID sourceEventId,
        String sourceEventType,
        String idempotencyKey
) {
}
