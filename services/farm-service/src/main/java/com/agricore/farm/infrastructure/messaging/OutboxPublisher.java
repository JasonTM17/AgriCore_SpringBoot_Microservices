package com.agricore.farm.infrastructure.messaging;

import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling transactional outbox publisher for farm domain events.
 * Disabled in tests via agricore.outbox.publisher.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "agricore.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final long sendTimeoutMillis;

    public OutboxPublisher(
            OutboxJpaRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${agricore.outbox.publisher.send-timeout-ms:10000}") long sendTimeoutMillis
    ) {
        if (sendTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Outbox send timeout must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(transactionTimeoutSeconds(sendTimeoutMillis));
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${agricore.outbox.publisher.poll-ms:1000}")
    public void publishPending() {
        List<UUID> eventIds = outboxRepository.findUnpublishedEventIds(PageRequest.of(0, 50));
        for (UUID eventId : eventIds) {
            transactionTemplate.executeWithoutResult(ignored -> publishLocked(eventId));
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
    }

    private void publishLocked(UUID eventId) {
        outboxRepository.findByIdForPublish(eventId)
                .filter(event -> event.getPublishedAt() == null)
                .ifPresent(this::publish);
    }

    private void publish(OutboxEventEntity event) {
        CompletableFuture<SendResult<String, String>> sendResult = null;
        try {
            sendResult = kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload());
            sendResult.get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
            event.markPublished();
            outboxRepository.save(event);
            log.debug("Published farm outbox event {} type={}", event.getId(), event.getEventType());
        } catch (TimeoutException ex) {
            sendResult.cancel(true);
            markFailed(event, "Kafka send timed out after " + sendTimeoutMillis + " ms");
        } catch (InterruptedException ex) {
            markFailed(event, "Kafka send interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            markFailed(event, failureMessage(ex));
        }
    }

    private void markFailed(OutboxEventEntity event, String message) {
        event.markFailed(message);
        outboxRepository.save(event);
        log.warn("Failed to publish farm outbox event {}: {}", event.getId(), message);
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

    private static int transactionTimeoutSeconds(long sendTimeoutMillis) {
        long totalMillis = Math.addExact(sendTimeoutMillis, 6_000);
        return Math.toIntExact(Math.max(1, (totalMillis + 999) / 1_000));
    }
}
