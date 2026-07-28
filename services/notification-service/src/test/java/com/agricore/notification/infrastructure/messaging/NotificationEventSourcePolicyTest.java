package com.agricore.notification.infrastructure.messaging;

import com.agricore.common.event.DomainEventEnvelopeReader;
import com.agricore.common.event.EventTypes;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventSourcePolicyTest {

    private static final String IDENTITY_TOPIC = "identity-topic";
    private static final String SALES_TOPIC = "sales-topic";
    private static final String TRACEABILITY_TOPIC = "traceability-topic";
    private static final String IOT_TOPIC = "iot-topic";

    private final NotificationEventSourcePolicy policy = new NotificationEventSourcePolicy(
            IDENTITY_TOPIC,
            SALES_TOPIC,
            TRACEABILITY_TOPIC,
            IOT_TOPIC
    );

    @Test
    void acceptsEveryKnownEventOnlyFromItsContractSource() {
        List<EventSourceCase> cases = List.of(
                new EventSourceCase(EventTypes.USER_REGISTERED, IDENTITY_TOPIC, "identity-service"),
                new EventSourceCase(EventTypes.SALES_ORDER_CONFIRMED, SALES_TOPIC, "sales-service"),
                new EventSourceCase(EventTypes.SALES_ORDER_CANCELLED, SALES_TOPIC, "sales-service"),
                new EventSourceCase(
                        EventTypes.TRACEABILITY_CODE_GENERATED,
                        TRACEABILITY_TOPIC,
                        "traceability-service"
                ),
                new EventSourceCase(EventTypes.SENSOR_READING_RECEIVED, IOT_TOPIC, "iot-service"),
                new EventSourceCase(EventTypes.SENSOR_THRESHOLD_EXCEEDED, IOT_TOPIC, "iot-service"),
                new EventSourceCase(EventTypes.DEVICE_OFFLINE_DETECTED, IOT_TOPIC, "iot-service")
        );

        for (EventSourceCase sourceCase : cases) {
            assertThatCode(() -> policy.validate(sourceCase.topic(), envelope(
                    sourceCase.eventType(),
                    sourceCase.producer()
            ))).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsUnsupportedEventsAndMismatchedSources() {
        assertThatThrownBy(() -> policy.validate(
                SALES_TOPIC,
                envelope("Unsupported.v1", "sales-service")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification event");

        assertThatThrownBy(() -> policy.validate(
                IOT_TOPIC,
                envelope(EventTypes.SALES_ORDER_CONFIRMED, "iot-service")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");
    }

    private static DomainEventEnvelopeReader.Envelope envelope(String eventType, String producer) {
        return new DomainEventEnvelopeReader.Envelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.parse("2026-07-22T08:00:00Z"),
                producer,
                NullNode.getInstance()
        );
    }

    private record EventSourceCase(String eventType, String topic, String producer) {
    }
}
