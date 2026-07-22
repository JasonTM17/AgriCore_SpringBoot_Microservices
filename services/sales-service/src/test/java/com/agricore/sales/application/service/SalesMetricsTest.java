package com.agricore.sales.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesMetricsTest {

    @Test
    void mapsInternalSagaStatesToBoundedOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SalesMetrics metrics = new SalesMetrics(registry);

        metrics.recordSagaOutcome("COMPLETED");
        metrics.recordSagaOutcome("unexpected-state");

        assertThat(registry.get("agricore.sales.sagas").tag("outcome", "completed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.sales.sagas").tag("outcome", "other").counter().count())
                .isEqualTo(1);
    }
}
