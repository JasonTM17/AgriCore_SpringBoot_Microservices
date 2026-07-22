package com.agricore.notification.infrastructure.messaging;

import com.agricore.common.event.DomainEventEnvelopeReader;
import com.agricore.common.event.EventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationEventSourcePolicy {

    private final Map<String, EventSource> sources;

    public NotificationEventSourcePolicy(
            @Value("${agricore.kafka.topics.sales-events:agricore.sales.events}") String salesTopic,
            @Value("${agricore.kafka.topics.traceability-events:agricore.traceability.events}") String traceabilityTopic,
            @Value("${agricore.kafka.topics.iot-events:agricore.iot.events}") String iotTopic
    ) {
        this.sources = Map.of(
                EventTypes.SALES_ORDER_CONFIRMED, new EventSource(salesTopic, "sales-service"),
                EventTypes.SALES_ORDER_CANCELLED, new EventSource(salesTopic, "sales-service"),
                EventTypes.TRACEABILITY_CODE_GENERATED,
                new EventSource(traceabilityTopic, "traceability-service"),
                EventTypes.SENSOR_THRESHOLD_EXCEEDED, new EventSource(iotTopic, "iot-service"),
                EventTypes.DEVICE_OFFLINE_DETECTED, new EventSource(iotTopic, "iot-service")
        );
    }

    public void validate(String topic, DomainEventEnvelopeReader.Envelope envelope) {
        EventSource expected = sources.get(envelope.eventType());
        if (expected == null) {
            throw new IllegalArgumentException("Unsupported notification event: " + envelope.eventType());
        }
        if (!expected.topic().equals(topic) || !expected.producer().equals(envelope.producer())) {
            throw new IllegalArgumentException("Notification event source does not match its contract");
        }
    }

    private record EventSource(String topic, String producer) {
    }
}
