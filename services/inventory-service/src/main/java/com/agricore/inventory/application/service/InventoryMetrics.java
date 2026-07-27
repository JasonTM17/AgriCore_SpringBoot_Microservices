package com.agricore.inventory.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class InventoryMetrics {

    private final Counter reservationSuccesses;
    private final Counter reservationFailures;
    private final Counter appliedHarvestEvents;
    private final Counter duplicateHarvestEvents;

    public InventoryMetrics(MeterRegistry registry) {
        reservationSuccesses = counter(registry, "agricore.inventory.reservations", "success");
        reservationFailures = counter(registry, "agricore.inventory.reservations", "insufficient_stock");
        appliedHarvestEvents = counter(registry, "agricore.inventory.harvest.events", "applied");
        duplicateHarvestEvents = counter(registry, "agricore.inventory.harvest.events", "duplicate");
    }

    public void recordReservationSuccess() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reservationSuccesses.increment();
                }
            });
            return;
        }
        reservationSuccesses.increment();
    }

    public void recordReservationFailure() {
        reservationFailures.increment();
    }

    public void recordAppliedHarvestEvent() {
        appliedHarvestEvents.increment();
    }

    public void recordDuplicateHarvestEvent() {
        duplicateHarvestEvents.increment();
    }

    private static Counter counter(MeterRegistry registry, String name, String outcome) {
        return Counter.builder(name)
                .description("AgriCore inventory business operation outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
