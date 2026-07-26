package com.agricore.harvest.infrastructure.observability;

import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxBacklogMetrics {

    public OutboxBacklogMetrics(MeterRegistry registry, OutboxJpaRepository repository) {
        Gauge.builder("agricore.outbox.backlog", repository,
                        OutboxJpaRepository::countByPublishedAtIsNull)
                .description("All unpublished transactional outbox events")
                .register(registry);
        Gauge.builder("agricore.outbox.pending", repository,
                        OutboxJpaRepository::countByPublishedAtIsNullAndQuarantinedAtIsNull)
                .description("Unpublished non-quarantined transactional outbox events")
                .register(registry);
        Gauge.builder("agricore.outbox.quarantined", repository,
                        OutboxJpaRepository::countByQuarantinedAtIsNotNull)
                .description("Quarantined transactional outbox events")
                .register(registry);
    }
}
