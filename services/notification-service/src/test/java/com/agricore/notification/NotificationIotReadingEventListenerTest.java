package com.agricore.notification;

import com.agricore.notification.application.service.NotificationApplicationService;
import com.agricore.notification.infrastructure.messaging.NotificationEventListener;
import com.agricore.notification.infrastructure.messaging.NotificationEventSourcePolicy;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIotReadingEventListenerTest {

    @Autowired
    private NotificationApplicationService notificationService;
    @Autowired
    private NotificationJpaRepository notificationRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationEventSourcePolicy eventSourcePolicy;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
        outboxRepository.deleteAll();
        listener = new NotificationEventListener(notificationService, objectMapper, eventSourcePolicy);
    }

    @Test
    void sourceValidatedReadingIsIgnoredWithoutNotificationSideEffects() {
        String raw = reading("iot-service", 1);

        assertThatCode(() -> listener.onMessage(record("agricore.iot.events", raw)))
                .doesNotThrowAnyException();

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void forgedOrWrongVersionReadingIsRejectedWithoutSideEffects() {
        assertThatThrownBy(() -> listener.onMessage(record(
                "agricore.iot.events",
                reading("farm-service", 1)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");
        assertThatThrownBy(() -> listener.onMessage(record(
                "agricore.sales.events",
                reading("iot-service", 1)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");
        assertThatThrownBy(() -> listener.onMessage(record(
                "agricore.iot.events",
                reading("iot-service", 2)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification event");

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void sourceMatchedMalformedReadingIsRejectedWithoutSideEffects() {
        String malformedReading = reading("iot-service", 1)
                .replace("\"metricValue\":41.5000", "\"metricValue\":\"41.5000\"");

        assertThatThrownBy(() -> listener.onMessage(record("agricore.iot.events", malformedReading)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metricValue");

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void sourceMatchedSchemaValidBoundaryReadingIsIgnoredWithoutSideEffects() {
        String schemaValidReading = reading("iot-service", 1)
                .replace("\"deviceCode\":\"SENSOR-001\"", "\"deviceCode\":\"sensor / field\"")
                .replace("\"metricValue\":41.5000", "\"metricValue\":12345678901.12345");

        assertThatCode(() -> listener.onMessage(record("agricore.iot.events", schemaValidReading)))
                .doesNotThrowAnyException();

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private static String reading(String producer, int eventVersion) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"SensorReadingReceived.v1",
                  "eventVersion":%s,
                  "occurredAt":"2026-07-28T08:00:00Z",
                  "producer":"%s",
                  "payload":{
                    "readingId":"%s",
                    "deviceId":"%s",
                    "deviceCode":"SENSOR-001",
                    "plotId":"%s",
                    "metricType":"SOIL_MOISTURE",
                    "metricValue":41.5000,
                    "unit":"PERCENT",
                    "recordedAt":"2026-07-28T08:00:00Z"
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                eventVersion,
                producer,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    private static ConsumerRecord<String, String> record(String topic, String raw) {
        return new ConsumerRecord<>(topic, 0, 0L, "event-key", raw);
    }
}
