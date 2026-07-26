package com.agricore.notification.application.service;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);
    private final NotificationPersistenceService persistenceService;
    private final NotificationDeliveryPort deliveryPort;
    private final NotificationMetrics metrics;
    private final int maxAttempts;
    private final Duration deliveryLease;

    public NotificationApplicationService(
            NotificationPersistenceService persistenceService,
            NotificationDeliveryPort deliveryPort,
            NotificationMetrics metrics,
            @Value("${agricore.notification.delivery.max-attempts:3}") int maxAttempts,
            @Value("${agricore.notification.delivery.lease:PT30S}") Duration deliveryLease
    ) {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("Notification max attempts must be between 1 and 5");
        }
        if (deliveryLease.isNegative() || deliveryLease.isZero()) {
            throw new IllegalArgumentException("Notification delivery lease must be positive");
        }
        this.persistenceService = persistenceService;
        this.deliveryPort = deliveryPort;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.deliveryLease = deliveryLease;
    }

    public NotificationResponse send(SendNotificationRequest request) {
        NotificationDraft draft = NotificationMapper.fromDirectRequest(request);
        NotificationEntity notification = createOrFind(draft);
        NotificationMapper.assertSameIntent(notification, draft);
        DeliveryExecution execution = deliver(notification.getId());
        if (!execution.transitioned() && draft.idempotencyKey() != null) {
            metrics.recordDuplicate();
        }
        return NotificationMapper.toResponse(execution.notification());
    }

    public boolean consume(NotificationEventCommand command) {
        if (persistenceService.isProcessed(command.eventId())) {
            metrics.recordDuplicate();
            return false;
        }
        NotificationDraft draft = NotificationMapper.fromEvent(command);
        NotificationEntity notification = createOrFind(draft);
        DeliveryExecution execution = deliver(notification.getId());
        if (!execution.transitioned()) {
            metrics.recordDuplicate();
            return false;
        }
        return true;
    }

    public void retryExisting(UUID notificationId) {
        deliver(notificationId);
    }

    private NotificationEntity createOrFind(NotificationDraft draft) {
        Optional<NotificationEntity> existing = findExisting(draft);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return persistenceService.createRequested(draft);
        } catch (DataIntegrityViolationException exception) {
            return findExisting(draft).orElseThrow(() -> exception);
        }
    }

    private Optional<NotificationEntity> findExisting(NotificationDraft draft) {
        if (draft.sourceEventId() != null) {
            return persistenceService.findBySourceEventId(draft.sourceEventId());
        }
        if (draft.idempotencyKey() != null) {
            return persistenceService.findByIdempotencyKey(draft.idempotencyKey());
        }
        return Optional.empty();
    }

    private DeliveryExecution deliver(UUID notificationId) {
        Instant staleBefore = Instant.now().minus(deliveryLease);
        Optional<NotificationPersistenceService.Claim> claimed = persistenceService.claimDelivery(notificationId, staleBefore);
        if (claimed.isEmpty()) {
            return new DeliveryExecution(persistenceService.findById(notificationId), false);
        }

        NotificationPersistenceService.Claim deliveryClaim = claimed.get();
        NotificationEntity notification = deliveryClaim.notification();
        NotificationDeliveryResult result = NotificationDeliveryResult.failed(
                "RETRY_BUDGET_EXHAUSTED", "Notification delivery retry budget exhausted", false);
        while (true) {
            if (persistenceService.beginAttempt(notificationId, deliveryClaim.claimId(), maxAttempts).isEmpty()) {
                break;
            }
            result = safelyDeliver(notification, deliveryClaim.claimId());
            if (result.delivered()
                    || !result.retryable()
                    || !supportsAutomaticRetry(notification.getChannel())) {
                break;
            }
        }

        NotificationPersistenceService.Completion completion = persistenceService.completeDelivery(
                notificationId, deliveryClaim.claimId(), Objects.requireNonNull(result));
        NotificationEntity completed = completion.notification();
        if (completion.transitioned()) {
            if (result.delivered()) {
                metrics.recordDelivered();
            } else {
                metrics.recordFailed();
            }
            log.info("notification_delivery_completed notificationId={} channel={} status={} attempts={} sourceEventType={}",
                    completed.getId(), completed.getChannel(), completed.getStatus(),
                    completed.getDeliveryAttempts(), completed.getSourceEventType());
        }
        return new DeliveryExecution(completed, completion.transitioned());
    }

    private NotificationDeliveryResult safelyDeliver(NotificationEntity notification, UUID claimId) {
        try {
            NotificationDeliveryResult result = deliveryPort.deliver(new NotificationDeliveryRequest(
                    notification.getId(), claimId, notification.getChannel(), notification.getRecipient(),
                    notification.getSubject(), notification.getBody()
            ));
            if (result == null) {
                throw new IllegalStateException("Delivery adapter returned no result");
            }
            return result;
        } catch (RuntimeException exception) {
            log.warn("notification_adapter_error notificationId={} channel={} errorType={}",
                    notification.getId(), notification.getChannel(), exception.getClass().getSimpleName());
            return NotificationDeliveryResult.failed(
                    "DELIVERY_ADAPTER_ERROR", "Notification delivery adapter failed", true);
        }
    }

    private static boolean supportsAutomaticRetry(String channel) {
        return "IN_APP".equalsIgnoreCase(channel);
    }

    private record DeliveryExecution(NotificationEntity notification, boolean transitioned) {
    }
}
