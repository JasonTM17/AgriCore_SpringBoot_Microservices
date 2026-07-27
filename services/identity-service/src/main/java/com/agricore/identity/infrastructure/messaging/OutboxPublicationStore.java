package com.agricore.identity.infrastructure.messaging;

import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the short database transactions around outbox publication.
 * Kafka I/O stays in {@link OutboxPublisher}, outside these boundaries.
 */
@Component
class OutboxPublicationStore {

    private static final int BATCH_SIZE = 50;

    private final OutboxJpaRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;

    OutboxPublicationStore(
            OutboxJpaRepository outboxRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    List<UUID> findPublishableEventIds() {
        return outboxRepository.findPublishableEventIds(PageRequest.of(0, BATCH_SIZE));
    }

    ClaimedEvent claim(UUID eventId, UUID claimToken, long claimLeaseMillis) {
        return transactionTemplate.execute(status -> {
            Instant databaseTime = outboxRepository.currentTimestamp();
            long monotonicNanos = System.nanoTime();
            int claimed = outboxRepository.claimForPublish(
                    eventId,
                    claimToken,
                    databaseTime.plusMillis(claimLeaseMillis)
            );
            if (claimed == 0) {
                return null;
            }
            return outboxRepository.findByIdAndClaimToken(eventId, claimToken)
                    .map(event -> ClaimedEvent.from(event, databaseTime, monotonicNanos))
                    .orElseThrow(() -> new IllegalStateException(
                            "Claimed identity outbox event could not be reloaded: " + eventId
                    ));
        });
    }

    boolean complete(ClaimedEvent event) {
        Integer completed = transactionTemplate.execute(status -> outboxRepository.completePublish(
                event.id(),
                event.claimToken(),
                outboxRepository.currentTimestamp()
        ));
        return completed != null && completed == 1;
    }

    boolean fail(
            ClaimedEvent event,
            String message,
            Instant nextAttemptAt,
            Instant quarantinedAt
    ) {
        Integer failed = transactionTemplate.execute(status ->
                outboxRepository.failPublish(
                        event.id(),
                        event.claimToken(),
                        message,
                        nextAttemptAt,
                        quarantinedAt
                )
        );
        return failed != null && failed == 1;
    }

    record ClaimedEvent(
            UUID id,
            String eventType,
            String topic,
            String payload,
            UUID claimToken,
            int publishAttempts,
            Instant databaseTime,
            long monotonicNanos
    ) {
        private static ClaimedEvent from(
                OutboxEventEntity event,
                Instant databaseTime,
                long monotonicNanos
        ) {
            return new ClaimedEvent(
                    event.getId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPayload(),
                    event.getClaimToken(),
                    event.getPublishAttempts(),
                    databaseTime,
                    monotonicNanos
            );
        }

        Instant failureTime() {
            long elapsedNanos = Math.max(0, System.nanoTime() - monotonicNanos);
            return databaseTime.plusNanos(elapsedNanos);
        }
    }
}
