package com.agricore.iot.infrastructure.messaging;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttTelemetryListener listener;

    public MqttHealthIndicator(ObjectProvider<MqttTelemetryListener> listenerProvider) {
        this.listener = listenerProvider.getIfAvailable();
    }

    @Override
    public Health health() {
        if (listener == null) {
            return Health.up().withDetail("protocol", "mqtt").withDetail("enabled", false).build();
        }
        if (listener.isReady()) {
            return Health.up().withDetail("protocol", "mqtt").build();
        }
        return Health.down().withDetail("protocol", "mqtt").build();
    }
}
