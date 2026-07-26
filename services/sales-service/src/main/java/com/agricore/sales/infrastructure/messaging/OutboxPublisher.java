package com.agricore.sales.infrastructure.messaging;

import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "agricore.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final long sendTimeoutMillis;
    private final OutboxRetryProperties retryProperties;

    public OutboxPublisher(
            OutboxJpaRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${agricore.outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMillis,
            OutboxRetryProperties retryProperties
    ) {
        if (sendTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Outbox send timeout must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(transactionTimeoutSeconds(sendTimeoutMillis));
        this.sendTimeoutMillis = sendTimeoutMillis;
        this.retryProperties = retryProperties;
    }

    @Scheduled(fixedDelayString = "${agricore.outbox.publisher.poll-ms:1000}")
    public void publishPending() {
        java.time.Instant now = outboxRepository.currentTimestamp();
        List<UUID> eventIds = outboxRepository.findUnpublishedEventIds(now, PageRequest.of(0, 50));
        for (UUID eventId : eventIds) {
            transactionTemplate.executeWithoutResult(ignored -> publishLocked(eventId, now));
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
    }

    private void publishLocked(UUID eventId, java.time.Instant now) {
        PublicationClock clock = PublicationClock.startingAt(outboxRepository.currentTimestamp());
        outboxRepository.findByIdForPublish(eventId, now)
                .filter(event -> event.isEligibleForPublish(now))
                .ifPresent(event -> publish(event, clock));
    }

    private void publish(OutboxEventEntity event, PublicationClock clock) {
        CompletableFuture<SendResult<String, String>> sendResult = null;
        try {
            sendResult = kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload());
            sendResult.get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
            event.markPublished();
            outboxRepository.save(event);
            log.debug("Published sales outbox event {} type={}", event.getId(), event.getEventType());
        } catch (TimeoutException exception) {
            sendResult.cancel(true);
            markFailed(event, "Kafka send timed out after " + sendTimeoutMillis + " ms", clock.now(), false);
        } catch (InterruptedException exception) {
            markFailed(event, "Kafka send interrupted", clock.now(), false);
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            markFailed(event, failureMessage(exception), clock.now(), isPermanentFailure(exception));
        }
    }

    private void markFailed(OutboxEventEntity event, String message, java.time.Instant failedAt, boolean permanentFailure) {
        if (!retryProperties.writeStateEnabled()) {
            event.markFailedWithoutRetryState(message);
            outboxRepository.save(event);
            log.warn("Failed to publish sales outbox event {} while retry-state writes are disabled: {}",
                    event.getId(), message);
            return;
        }
        int failedAttempt = event.getPublishAttempts() + 1;
        event.markFailed(
                message,
                failedAt,
                retryProperties.delayForFailure(failedAttempt),
                permanentFailure ? retryProperties.maxAttempts() : Integer.MAX_VALUE
        );
        outboxRepository.save(event);
        if (event.getQuarantinedAt() != null) {
            log.error("Quarantined sales outbox event {} after {} attempts: {}",
                    event.getId(), event.getPublishAttempts(), message);
        } else {
            log.warn("Failed to publish sales outbox event {} (retry at {}): {}",
                    event.getId(), event.getNextAttemptAt(), message);
        }
    }

    private static String failureMessage(Exception exception) {
        Throwable cause = failureCause(exception);
        String message = cause.getMessage();
        String diagnostic = message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
        return boundedDiagnostic(diagnostic);
    }

    private static boolean isPermanentFailure(Exception exception) {
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++) {
            if (cause instanceof org.apache.kafka.common.errors.SerializationException
                    || cause instanceof org.apache.kafka.common.errors.RecordTooLargeException
                    || cause instanceof org.apache.kafka.common.errors.InvalidTopicException) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        return false;
    }

    private static Throwable failureCause(Exception exception) {
        Throwable cause = exception;
        for (int depth = 0; depth < 16; depth++) {
            Throwable next = cause.getCause();
            if (next == null || next == cause) {
                return cause;
            }
            cause = next;
        }
        return cause;
    }

    private static String boundedDiagnostic(String diagnostic) {
        String bounded = diagnostic.substring(0, Math.min(diagnostic.length(), 1_000));
        return bounded.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    private static int transactionTimeoutSeconds(long sendTimeoutMillis) {
        long totalMillis = Math.addExact(sendTimeoutMillis, 6_000);
        return Math.toIntExact(Math.max(1, (totalMillis + 999) / 1_000));
    }

    private record PublicationClock(java.time.Instant databaseTime, long monotonicNanos) {
        static PublicationClock startingAt(java.time.Instant databaseTime) {
            return new PublicationClock(databaseTime, System.nanoTime());
        }

        java.time.Instant now() {
            return databaseTime.plusNanos(System.nanoTime() - monotonicNanos);
        }
    }
}
