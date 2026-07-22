package com.agricore.cropcycle.infrastructure.observability;

import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxBacklogMetrics {

    public OutboxBacklogMetrics(MeterRegistry registry, OutboxJpaRepository repository) {
        Gauge.builder("agricore.outbox.backlog", repository,
                        OutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished transactional outbox events")
                .register(registry);
    }
}
