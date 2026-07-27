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
    private final Counter mqttAccepted;
    private final Counter mqttRejected;
    private final Counter mqttOversized;
    private final Counter mqttRateLimited;
    private final Counter mqttConnectionFailures;
    private final Counter mqttProcessingFailures;

    public IotMetrics(MeterRegistry registry, SensorAlertJpaRepository alertRepository) {
        readings = Counter.builder("agricore.iot.readings")
                .description("Accepted IoT sensor readings")
                .register(registry);
        createdAlerts = alertCounter(registry, "created");
        suppressedAlerts = alertCounter(registry, "suppressed");
        mqttAccepted = mqttCounter(registry, "accepted");
        mqttRejected = mqttCounter(registry, "rejected");
        mqttOversized = mqttCounter(registry, "oversized");
        mqttRateLimited = mqttCounter(registry, "rate_limited");
        mqttConnectionFailures = mqttCounter(registry, "connection_failed");
        mqttProcessingFailures = mqttCounter(registry, "processing_failed");
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

    public void recordMqttOutcome(String outcome) {
        switch (outcome) {
            case "accepted" -> mqttAccepted.increment();
            case "rejected" -> mqttRejected.increment();
            case "oversized" -> mqttOversized.increment();
            case "rate_limited" -> mqttRateLimited.increment();
            case "connection_failed" -> mqttConnectionFailures.increment();
            case "processing_failed" -> mqttProcessingFailures.increment();
            default -> throw new IllegalArgumentException("Unsupported MQTT metric outcome");
        }
    }

    private static Counter alertCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("agricore.iot.alerts")
                .description("IoT alert evaluation outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static Counter mqttCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("agricore.iot.mqtt.messages")
                .description("MQTT telemetry adapter outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
