package com.agricore.sales.infrastructure.observability;

import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxBacklogMetrics {

    public OutboxBacklogMetrics(MeterRegistry registry, OutboxJpaRepository repository) {
        Gauge.builder("agricore.outbox.backlog", repository, OutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished transactional outbox events")
                .register(registry);
    }
}
