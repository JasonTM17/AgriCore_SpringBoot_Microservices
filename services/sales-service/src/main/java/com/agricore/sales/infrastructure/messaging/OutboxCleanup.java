package com.agricore.sales.infrastructure.messaging;

import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "agricore.outbox.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanup {

    private final OutboxJpaRepository outboxRepository;
    private final Duration retention;
    private final Duration quarantineRetention;
    private final int batchSize;

    public OutboxCleanup(
            OutboxJpaRepository outboxRepository,
            @Value("${agricore.outbox.cleanup.retention:P7D}") Duration retention,
            @Value("${agricore.outbox.cleanup.quarantine-retention:P7D}") Duration quarantineRetention,
            @Value("${agricore.outbox.cleanup.batch-size:500}") int batchSize
    ) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Outbox retention must be positive");
        }
        if (quarantineRetention.isNegative() || quarantineRetention.isZero()) {
            throw new IllegalArgumentException("Outbox quarantine retention must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox cleanup batch size must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.retention = retention;
        this.quarantineRetention = quarantineRetention;
        this.batchSize = batchSize;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${agricore.outbox.cleanup.poll-ms:3600000}")
    public void deleteExpiredTerminalEvents() {
        java.time.Instant now = outboxRepository.currentTimestamp();
        List<OutboxEventEntity> expiredEvents = outboxRepository.findTerminalEventsForCleanup(
                now.minus(retention),
                now.minus(quarantineRetention),
                batchSize
        );
        if (!expiredEvents.isEmpty()) {
            outboxRepository.deleteAllInBatch(expiredEvents);
        }
    }
}
