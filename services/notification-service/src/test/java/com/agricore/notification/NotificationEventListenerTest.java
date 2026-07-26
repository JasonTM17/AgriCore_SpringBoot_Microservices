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
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationEventListenerTest {

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
    void duplicateSalesConfirmationCreatesOneNotificationAndOneProcessedMarker() {
        UUID eventId = UUID.randomUUID();
        String raw = """
                {
                  "eventId":"%s",
                  "eventType":"SalesOrderConfirmed.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-22T08:00:00Z",
                  "correlationId":"corr-sales-1",
                  "producer":"sales-service",
                  "payload":{
                    "salesOrderId":"%s",
                    "orderNumber":"SO-100",
                    "customerId":"%s",
                    "inventoryItemId":"%s",
                    "quantity":10,
                    "status":"CONFIRMED",
                    "reservationId":"%s",
                    "confirmedAt":"2026-07-22T08:00:00Z"
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        listener.onMessage(record("agricore.sales.events", raw));
        listener.onMessage(record("agricore.sales.events", raw));

        assertThat(notificationRepository.countBySourceEventId(eventId)).isEqualTo(1);
        assertThat(processedEventRepository.existsByEventIdAndConsumerName(eventId, "notification-service"))
                .isTrue();
        assertThat(outboxRepository.findAll()).extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder("NotificationRequested.v2", "NotificationSent.v2");
        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getRecipient()).isNotBlank();
                    assertThat(notification.getStatus()).isEqualTo("SENT");
                    assertThat(notification.getCorrelationId()).isEqualTo("corr-sales-1");
                });
    }

    @Test
    void duplicateUserRegistrationCreatesOneWelcomeEmailAndOneProcessedMarker() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String raw = """
                {
                  "eventId":"%s",
                  "eventType":"UserRegistered.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-26T10:15:30Z",
                  "producer":"identity-service",
                  "payload":{
                    "userId":"%s",
                    "email":"worker@agricore.test",
                    "fullName":"Field Worker",
                    "roles":["FIELD_WORKER"],
                    "registeredAt":"2026-07-26T10:15:30Z"
                  }
                }
                """.formatted(eventId, userId);

        listener.onMessage(record("agricore.identity.events", raw));
        listener.onMessage(record("agricore.identity.events", raw));

        assertThat(notificationRepository.countBySourceEventId(eventId)).isEqualTo(1);
        assertThat(processedEventRepository.existsByEventIdAndConsumerName(eventId, "notification-service"))
                .isTrue();
        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getRecipient()).isEqualTo("worker@agricore.test");
                    assertThat(notification.getSubject()).isEqualTo("Welcome to AgriCore");
                    assertThat(notification.getStatus()).isEqualTo("SENT");
                    assertThat(notification.getBody()).contains("Field Worker", "FIELD_WORKER", userId.toString());
                });
    }

    @Test
    void forgedOrUnboundedUserRegistrationIsRejectedWithoutSideEffects() {
        String forgedProducer = userRegistration("farm-service", "worker@agricore.test", "");
        String invalidEmail = userRegistration("identity-service", "not-an-email", "");
        String extraPayload = userRegistration(
                "identity-service",
                "worker@agricore.test",
                ",\"passwordHash\":\"must-never-cross-boundary\""
        );

        assertThatThrownBy(() -> listener.onMessage(record("agricore.identity.events", forgedProducer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");
        assertThatThrownBy(() -> listener.onMessage(record("agricore.identity.events", invalidEmail)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid email");
        assertThatThrownBy(() -> listener.onMessage(record("agricore.identity.events", extraPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported field");

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void malformedOrUnsupportedEventsAreRejectedForDeadLetterHandling() {
        UUID eventId = UUID.randomUUID();
        String raw = """
                {
                  "eventId":"%s",
                  "eventType":"SalesOrderCreated.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-22T08:00:00Z",
                  "producer":"sales-service",
                  "payload":{}
                }
                """.formatted(eventId);

        assertThatThrownBy(() -> listener.onMessage(record("agricore.sales.events", raw)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification event");
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void forgedProducerOrWrongTopicIsRejectedBeforeAnySideEffect() {
        String forgedProducer = salesConfirmation("iot-service");
        String wrongTopic = salesConfirmation("sales-service");

        assertThatThrownBy(() -> listener.onMessage(record("agricore.sales.events", forgedProducer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");
        assertThatThrownBy(() -> listener.onMessage(record("agricore.iot.events", wrongTopic)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source does not match");

        assertThat(notificationRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void contractValidationFailuresBypassRetryTopics() throws NoSuchMethodException {
        Method listenerMethod = NotificationEventListener.class
                .getMethod("onMessage", ConsumerRecord.class);
        RetryableTopic retryPolicy = listenerMethod.getAnnotation(RetryableTopic.class);

        assertThat(retryPolicy).isNotNull();
        assertThat(Arrays.asList(retryPolicy.exclude())).contains(IllegalArgumentException.class);
    }

    private static ConsumerRecord<String, String> record(String topic, String raw) {
        return new ConsumerRecord<>(topic, 0, 0L, "event-key", raw);
    }

    private static String salesConfirmation(String producer) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"SalesOrderConfirmed.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-22T08:00:00Z",
                  "producer":"%s",
                  "payload":{
                    "salesOrderId":"%s",
                    "orderNumber":"SO-FORGED",
                    "customerId":"%s",
                    "inventoryItemId":"%s",
                    "quantity":1,
                    "status":"CONFIRMED",
                    "reservationId":"%s",
                    "confirmedAt":"2026-07-22T08:00:00Z"
                  }
                }
                """.formatted(
                UUID.randomUUID(), producer, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()
        );
    }

    private static String userRegistration(String producer, String email, String extraPayload) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"UserRegistered.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-26T10:15:30Z",
                  "producer":"%s",
                  "payload":{
                    "userId":"%s",
                    "email":"%s",
                    "fullName":"Field Worker",
                    "roles":["FIELD_WORKER"],
                    "registeredAt":"2026-07-26T10:15:30Z"%s
                  }
                }
                """.formatted(UUID.randomUUID(), producer, UUID.randomUUID(), email, extraPayload);
    }
}
