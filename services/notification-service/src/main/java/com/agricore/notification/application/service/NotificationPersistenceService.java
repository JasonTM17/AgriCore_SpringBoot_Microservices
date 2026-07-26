package com.agricore.notification.application.service;

import com.agricore.notification.application.port.NotificationDeliveryResult;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import com.agricore.notification.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationPersistenceService {

    private static final String CONSUMER_NAME = "notification-service";
    private static final int ERROR_CODE_LIMIT = 100;
    private static final int ERROR_MESSAGE_LIMIT = 500;

    private final NotificationJpaRepository repository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final NotificationEventOutboxWriter eventOutboxWriter;

    public NotificationPersistenceService(
            NotificationJpaRepository repository,
            ProcessedEventJpaRepository processedEventRepository,
            NotificationEventOutboxWriter eventOutboxWriter
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.eventOutboxWriter = eventOutboxWriter;
    }

    @Transactional
    public NotificationEntity createRequested(NotificationDraft draft) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setChannel(draft.channel());
        notification.setRecipient(draft.recipient());
        notification.setSubject(draft.subject());
        notification.setBody(draft.body());
        notification.setCorrelationId(draft.correlationId());
        notification.setSourceEventId(draft.sourceEventId());
        notification.setSourceEventType(draft.sourceEventType());
        notification.setIdempotencyKey(draft.idempotencyKey());
        notification.setStatus("REQUESTED");
        notification.setCreatedAt(Instant.now());
        repository.saveAndFlush(notification);
        eventOutboxWriter.notificationRequested(notification, draft.sourceEventType());
        return notification;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationEntity> findBySourceEventId(UUID sourceEventId) {
        return repository.findBySourceEventId(sourceEventId);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public NotificationEntity findById(UUID notificationId) {
        return repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    @Transactional
    public Optional<Claim> claimDelivery(UUID notificationId, Instant staleBefore) {
        NotificationEntity notification = repository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (isTerminal(notification.getStatus())) {
            return Optional.empty();
        }
        if ("DELIVERING".equals(notification.getStatus())
                && notification.getDeliveryStartedAt() != null
                && !notification.getDeliveryStartedAt().isBefore(staleBefore)) {
            return Optional.empty();
        }
        notification.setStatus("DELIVERING");
        notification.setDeliveryStartedAt(Instant.now());
        UUID claimId = UUID.randomUUID();
        notification.setDeliveryClaimId(claimId);
        return Optional.of(new Claim(repository.saveAndFlush(notification), claimId));
    }

    @Transactional
    public Optional<Integer> beginAttempt(UUID notificationId, UUID claimId, int maxAttempts) {
        NotificationEntity notification = repository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!"DELIVERING".equals(notification.getStatus())
                || !claimId.equals(notification.getDeliveryClaimId())
                || notification.getDeliveryAttempts() >= maxAttempts) {
            return Optional.empty();
        }
        int attempt = notification.getDeliveryAttempts() + 1;
        notification.setDeliveryAttempts(attempt);
        notification.setDeliveryStartedAt(Instant.now());
        repository.saveAndFlush(notification);
        return Optional.of(attempt);
    }

    @Transactional
    public Completion completeDelivery(
            UUID notificationId,
            UUID claimId,
            NotificationDeliveryResult result
    ) {
        NotificationEntity notification = repository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (isTerminal(notification.getStatus())) {
            return new Completion(notification, false);
        }
        if (!"DELIVERING".equals(notification.getStatus())
                || !claimId.equals(notification.getDeliveryClaimId())) {
            return new Completion(notification, false);
        }

        applyCompletion(notification, result);
        return new Completion(repository.saveAndFlush(notification), true);
    }

    private void applyCompletion(NotificationEntity notification, NotificationDeliveryResult result) {
        notification.setDeliveryStartedAt(null);
        notification.setDeliveryClaimId(null);
        if (result.delivered()) {
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            eventOutboxWriter.notificationSent(notification, notification.getSourceEventType());
        } else {
            notification.setStatus("FAILED");
            notification.setFailedAt(Instant.now());
            notification.setErrorCode(bounded(result.errorCode(), "DELIVERY_FAILED", ERROR_CODE_LIMIT));
            notification.setErrorMessage(bounded(result.errorMessage(), "Notification delivery failed", ERROR_MESSAGE_LIMIT));
            notification.setFailureRetryable(result.retryable());
            eventOutboxWriter.notificationFailed(notification, notification.getSourceEventType());
        }
        if (notification.getSourceEventId() != null
                && !processedEventRepository.existsByEventIdAndConsumerName(
                notification.getSourceEventId(), CONSUMER_NAME)) {
            processedEventRepository.save(ProcessedEventEntity.create(
                    notification.getSourceEventId(), CONSUMER_NAME));
        }
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(UUID eventId) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME);
    }

    @Transactional(readOnly = true)
    public List<UUID> findRecoverableIds(Instant staleBefore, int batchSize) {
        return repository.findRecoverableIds(staleBefore, PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    public List<UUID> findAmbiguousExternalDeliveryIds(Instant staleBefore, int batchSize) {
        return repository.findAmbiguousExternalDeliveryIds(staleBefore, PageRequest.of(0, batchSize));
    }

    @Transactional
    public boolean failAmbiguousExternalDelivery(UUID notificationId, Instant staleBefore) {
        NotificationEntity notification = repository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!"DELIVERING".equals(notification.getStatus())
                || "IN_APP".equalsIgnoreCase(notification.getChannel())
                || notification.getDeliveryStartedAt() == null
                || !notification.getDeliveryStartedAt().isBefore(staleBefore)) {
            return false;
        }
        applyCompletion(notification, NotificationDeliveryResult.failed(
                "DELIVERY_OUTCOME_UNKNOWN",
                "External delivery outcome is unknown; automatic retry is disabled to prevent duplicates",
                false
        ));
        repository.saveAndFlush(notification);
        return true;
    }

    private static boolean isTerminal(String status) {
        return "SENT".equals(status) || "FAILED".equals(status);
    }

    private static String bounded(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record Completion(NotificationEntity notification, boolean transitioned) {
    }

    public record Claim(NotificationEntity notification, UUID claimId) {
    }
}
