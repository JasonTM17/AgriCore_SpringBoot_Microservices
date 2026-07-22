package com.agricore.sales.infrastructure.messaging;

import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "agricore.outbox.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanup {

    private final OutboxJpaRepository outboxRepository;
    private final Duration retention;
    private final int batchSize;

    public OutboxCleanup(
            OutboxJpaRepository outboxRepository,
            @Value("${agricore.outbox.cleanup.retention:P7D}") Duration retention,
            @Value("${agricore.outbox.cleanup.batch-size:500}") int batchSize
    ) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Outbox retention must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox cleanup batch size must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${agricore.outbox.cleanup.poll-ms:3600000}")
    public void deleteExpiredPublishedEvents() {
        List<UUID> expiredEventIds = outboxRepository.findPublishedEventIdsBefore(
                Instant.now().minus(retention),
                PageRequest.of(0, batchSize)
        );
        if (!expiredEventIds.isEmpty()) {
            outboxRepository.deleteAllByIdInBatch(expiredEventIds);
        }
    }
}
