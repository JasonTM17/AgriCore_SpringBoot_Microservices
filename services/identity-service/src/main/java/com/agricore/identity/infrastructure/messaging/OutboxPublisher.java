package com.agricore.identity.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling transactional outbox publisher for identity domain events.
 * Disabled in tests via agricore.outbox.publisher.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "agricore.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long MAX_SEND_TIMEOUT_MILLIS = 60_000;
    private static final long MAX_CLAIM_LEASE_MILLIS = 300_000;
    private static final int MAX_ERROR_LENGTH = 1_000;

    private final OutboxPublicationStore publicationStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMillis;
    private final long claimLeaseMillis;

    public OutboxPublisher(
            OutboxPublicationStore publicationStore,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${agricore.outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMillis,
            @Value("${agricore.outbox.publisher.claim-lease-ms:30000}") long claimLeaseMillis
    ) {
        validateDurations(sendTimeoutMillis, claimLeaseMillis);
        this.publicationStore = publicationStore;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMillis = sendTimeoutMillis;
        this.claimLeaseMillis = claimLeaseMillis;
    }

    @Scheduled(fixedDelayString = "${agricore.outbox.publisher.poll-ms:1000}")
    public void publishPending() {
        List<UUID> eventIds;
        try {
            eventIds = publicationStore.findPublishableEventIds();
        } catch (RuntimeException exception) {
            log.error("Could not scan the identity outbox for publishable events", exception);
            return;
        }
        for (UUID eventId : eventIds) {
            try {
                publishClaimed(eventId);
            } catch (RuntimeException exception) {
                log.error("Identity outbox processing failed for event {}", eventId, exception);
            }
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
    }

    private void publishClaimed(UUID eventId) {
        UUID claimToken = UUID.randomUUID();
        OutboxPublicationStore.ClaimedEvent event =
                publicationStore.claim(eventId, claimToken, claimLeaseMillis);
        if (event == null) {
            return;
        }

        CompletableFuture<SendResult<String, String>> sendResult = null;
        try {
            sendResult = kafkaTemplate.send(event.topic(), event.id().toString(), event.payload());
            sendResult.get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
            complete(event);
        } catch (TimeoutException exception) {
            sendResult.cancel(true);
            fail(event, "Kafka send timed out after " + sendTimeoutMillis + " ms");
        } catch (InterruptedException exception) {
            if (sendResult != null) {
                sendResult.cancel(true);
            }
            fail(event, "Kafka send interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            fail(event, failureMessage(exception));
        }
    }

    private void complete(OutboxPublicationStore.ClaimedEvent event) {
        try {
            if (!publicationStore.complete(event)) {
                log.warn("Identity outbox claim expired before completion for event {}", event.id());
                return;
            }
            log.debug("Published identity outbox event {} type={}", event.id(), event.eventType());
        } catch (RuntimeException exception) {
            log.error(
                    "Kafka accepted identity outbox event {}, but completion persistence failed",
                    event.id(),
                    exception
            );
        }
    }

    private void fail(OutboxPublicationStore.ClaimedEvent event, String message) {
        String boundedMessage = boundedError(message);
        try {
            if (!publicationStore.fail(event, boundedMessage)) {
                log.warn("Identity outbox claim expired before failure persistence for event {}", event.id());
                return;
            }
            log.warn("Failed to publish identity outbox event {}: {}", event.id(), boundedMessage);
        } catch (RuntimeException exception) {
            log.error("Could not persist identity outbox failure for event {}", event.id(), exception);
        }
    }

    private static String failureMessage(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }

    private static String boundedError(String message) {
        String normalized = message == null || message.isBlank() ? "unknown" : message;
        return normalized.substring(0, Math.min(normalized.length(), MAX_ERROR_LENGTH));
    }

    private static void validateDurations(long sendTimeoutMillis, long claimLeaseMillis) {
        if (sendTimeoutMillis <= 0 || sendTimeoutMillis > MAX_SEND_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(
                    "Outbox send timeout must be between 1 and " + MAX_SEND_TIMEOUT_MILLIS + " ms"
            );
        }
        if (claimLeaseMillis <= sendTimeoutMillis || claimLeaseMillis > MAX_CLAIM_LEASE_MILLIS) {
            throw new IllegalArgumentException(
                    "Outbox claim lease must exceed the send timeout and be at most "
                            + MAX_CLAIM_LEASE_MILLIS + " ms"
            );
        }
    }
}
