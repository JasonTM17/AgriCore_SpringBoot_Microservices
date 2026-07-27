package com.agricore.sales.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SalesMetrics {

    private final MeterRegistry registry;

    public SalesMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSagaOutcome(String status) {
        Counter.builder("agricore.sales.sagas")
                .description("Sales inventory saga terminal outcomes")
                .tag("outcome", safeOutcome(status))
                .register(registry)
                .increment();
    }

    private static String safeOutcome(String status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> "completed";
            case "FAILED" -> "failed";
            case "RECONCILED" -> "reconciled";
            default -> "other";
        };
    }
}
