package com.agricore.inventory.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMetricsTest {

    @Test
    void recordsOnlyBoundedReservationAndHarvestOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryMetrics metrics = new InventoryMetrics(registry);

        metrics.recordReservationSuccess();
        metrics.recordReservationFailure();
        metrics.recordAppliedHarvestEvent();
        metrics.recordDuplicateHarvestEvent();

        assertThat(registry.get("agricore.inventory.reservations").tag("outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.inventory.reservations").tag("outcome", "insufficient_stock").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.inventory.harvest.events").tag("outcome", "applied").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.inventory.harvest.events").tag("outcome", "duplicate").counter().count())
                .isEqualTo(1);
    }
}
