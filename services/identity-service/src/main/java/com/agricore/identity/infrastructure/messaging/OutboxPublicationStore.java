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
        return outboxRepository.findPublishableEventIds(
                Instant.now(),
                PageRequest.of(0, BATCH_SIZE)
        );
    }

    ClaimedEvent claim(UUID eventId, UUID claimToken, long claimLeaseMillis) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            int claimed = outboxRepository.claimForPublish(
                    eventId,
                    claimToken,
                    now,
                    now.plusMillis(claimLeaseMillis)
            );
            if (claimed == 0) {
                return null;
            }
            return outboxRepository.findByIdAndClaimToken(eventId, claimToken)
                    .map(ClaimedEvent::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "Claimed identity outbox event could not be reloaded: " + eventId
                    ));
        });
    }

    boolean complete(ClaimedEvent event) {
        Integer completed = transactionTemplate.execute(status ->
                outboxRepository.completePublish(event.id(), event.claimToken(), Instant.now())
        );
        return completed != null && completed == 1;
    }

    boolean fail(ClaimedEvent event, String message) {
        Integer failed = transactionTemplate.execute(status ->
                outboxRepository.failPublish(event.id(), event.claimToken(), message)
        );
        return failed != null && failed == 1;
    }

    record ClaimedEvent(
            UUID id,
            String eventType,
            String topic,
            String payload,
            UUID claimToken
    ) {
        private static ClaimedEvent from(OutboxEventEntity event) {
            return new ClaimedEvent(
                    event.getId(),
                    event.getEventType(),
                    event.getTopic(),
                    event.getPayload(),
                    event.getClaimToken()
            );
        }
    }
}
