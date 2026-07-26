package com.agricore.iot.infrastructure.observability;

import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
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
                .description("Unpublished transactional outbox events eligible for retry")
                .register(registry);
        Gauge.builder("agricore.outbox.quarantined", repository,
                        OutboxJpaRepository::countByQuarantinedAtIsNotNull)
                .description("Quarantined transactional outbox events")
                .register(registry);
    }
}
