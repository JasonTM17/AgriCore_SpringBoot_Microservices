package com.agricore.harvest.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarvestMetricsTest {

    @Test
    void recordsSuccessAndFailureLatencySeparately() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HarvestMetrics metrics = new HarvestMetrics(registry);

        assertThat(metrics.recordProcessing(() -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> metrics.recordProcessing(() -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("agricore.harvest.processing").tag("outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.harvest.processing").tag("outcome", "failure").timer().count())
                .isEqualTo(1);
    }
}
