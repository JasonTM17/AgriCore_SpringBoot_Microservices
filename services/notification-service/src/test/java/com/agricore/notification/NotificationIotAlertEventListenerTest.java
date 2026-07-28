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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIotAlertEventListenerTest {

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
    void abbreviatedThresholdPlotIdIsRejectedWithoutSideEffects() {
        assertInvalidPlotId(sensorThresholdExceeded("\"1-1-1-1-1\""), "UUID plotId");
    }

    @Test
    void abbreviatedOfflinePlotIdIsRejectedWithoutSideEffects() {
        assertInvalidPlotId(deviceOfflineDetected("\"1-1-1-1-1\""), "UUID plotId");
    }

    @Test
    void nonTextualOrBlankThresholdPlotIdsAreRejectedWithoutSideEffects() {
        for (String plotIdJson : new String[]{"null", "42", "{}", "\"\""}) {
            assertInvalidPlotId(sensorThresholdExceeded(plotIdJson), "requires plotId");
        }
    }

    @Test
    void canonicalThresholdPlotIdCreatesPlotNotification() {
        String plotId = UUID.randomUUID().toString();

        listener.onMessage(record(sensorThresholdExceeded("\"" + plotId + "\"")));

        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(notification -> assertThat(notification.getRecipient()).isEqualTo("plot:" + plotId));
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    private void assertInvalidPlotId(String raw, String expectedMessage) {
        assertThatThrownBy(() -> listener.onMessage(record(raw)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private static ConsumerRecord<String, String> record(String raw) {
        return new ConsumerRecord<>("agricore.iot.events", 0, 0L, "event-key", raw);
    }

    private static String sensorThresholdExceeded(String plotIdJson) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"SensorThresholdExceeded.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-28T08:00:00Z",
                  "producer":"iot-service",
                  "payload":{
                    "readingId":"%s",
                    "deviceId":"%s",
                    "deviceCode":"SOIL-01",
                    "plotId":%s,
                    "metricType":"SOIL_MOISTURE",
                    "metricValue":18.4,
                    "unit":"PERCENT",
                    "recordedAt":"2026-07-28T07:59:00Z",
                    "alertId":"%s",
                    "severity":"HIGH",
                    "ruleVersion":1,
                    "message":"Soil moisture is below the configured threshold.",
                    "detectedAt":"2026-07-28T08:00:00Z"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), plotIdJson, UUID.randomUUID());
    }

    private static String deviceOfflineDetected(String plotIdJson) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"DeviceOfflineDetected.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-28T08:00:00Z",
                  "producer":"iot-service",
                  "payload":{
                    "deviceId":"%s",
                    "deviceCode":"SOIL-01",
                    "plotId":%s,
                    "deviceName":"Soil Sensor 01",
                    "lastActivityAt":"2026-07-28T07:00:00Z",
                    "detectedAt":"2026-07-28T08:00:00Z",
                    "offlineAfterSeconds":3600
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), plotIdJson);
    }
}
