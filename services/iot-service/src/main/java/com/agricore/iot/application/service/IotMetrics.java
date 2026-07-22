package com.agricore.iot.application.service;

import com.agricore.iot.infrastructure.persistence.SensorAlertJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class IotMetrics {

    private final Counter readings;
    private final Counter createdAlerts;
    private final Counter suppressedAlerts;

    public IotMetrics(MeterRegistry registry, SensorAlertJpaRepository alertRepository) {
        readings = Counter.builder("agricore.iot.readings")
                .description("Accepted IoT sensor readings")
                .register(registry);
        createdAlerts = alertCounter(registry, "created");
        suppressedAlerts = alertCounter(registry, "suppressed");
        Gauge.builder("agricore.iot.open.alerts", alertRepository,
                        repository -> repository.countByStatus("OPEN"))
                .description("Open IoT sensor alerts")
                .register(registry);
    }

    public void recordReading() {
        readings.increment();
    }

    public void recordCreatedAlert() {
        createdAlerts.increment();
    }

    public void recordSuppressedAlert() {
        suppressedAlerts.increment();
    }

    private static Counter alertCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("agricore.iot.alerts")
                .description("IoT alert evaluation outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
