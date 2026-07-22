package com.agricore.notification.application.service;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;

final class NotificationMapper {

    private static final String DIRECT_SOURCE = "DirectApi";

    private NotificationMapper() {
    }

    static NotificationDraft fromDirectRequest(SendNotificationRequest request) {
        return new NotificationDraft(
                normalizeChannel(request.channel()),
                request.recipient().trim(),
                request.subject().trim(),
                request.body(),
                trimToNull(request.correlationId()),
                null,
                DIRECT_SOURCE,
                trimToNull(request.idempotencyKey())
        );
    }

    static NotificationDraft fromEvent(NotificationEventCommand command) {
        return new NotificationDraft(
                normalizeChannel(command.channel()),
                command.recipient().trim(),
                command.subject().trim(),
                command.body(),
                trimToNull(command.correlationId()),
                command.eventId(),
                command.eventType(),
                null
        );
    }

    static void assertSameIntent(NotificationEntity existing, NotificationDraft draft) {
        if (!Objects.equals(existing.getChannel(), draft.channel())
                || !Objects.equals(existing.getRecipient(), draft.recipient())
                || !Objects.equals(existing.getSubject(), draft.subject())
                || !Objects.equals(existing.getBody(), draft.body())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Idempotency key is already bound to another notification");
        }
    }

    static NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(), notification.getChannel(), notification.getRecipient(),
                notification.getSubject(), notification.getStatus(), notification.getCorrelationId(),
                notification.getCreatedAt(), notification.getSentAt(), notification.getFailedAt(),
                notification.getErrorCode(), notification.getErrorMessage(), notification.getFailureRetryable(),
                notification.getDeliveryAttempts()
        );
    }

    private static String normalizeChannel(String channel) {
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
