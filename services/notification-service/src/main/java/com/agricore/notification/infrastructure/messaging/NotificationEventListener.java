package com.agricore.notification.infrastructure.messaging;

import com.agricore.common.event.DomainEventEnvelopeReader;
import com.agricore.common.event.EventTypes;
import com.agricore.notification.application.service.NotificationApplicationService;
import com.agricore.notification.application.service.NotificationEventCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            EventTypes.SALES_ORDER_CONFIRMED,
            EventTypes.SALES_ORDER_CANCELLED,
            EventTypes.TRACEABILITY_CODE_GENERATED,
            EventTypes.SENSOR_THRESHOLD_EXCEEDED,
            EventTypes.DEVICE_OFFLINE_DETECTED
    );

    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;
    private final NotificationEventSourcePolicy eventSourcePolicy;

    public NotificationEventListener(
            NotificationApplicationService notificationService,
            ObjectMapper objectMapper,
            NotificationEventSourcePolicy eventSourcePolicy
    ) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.eventSourcePolicy = eventSourcePolicy;
    }

    @RetryableTopic(
            attempts = "${AGRICORE_KAFKA_RETRY_ATTEMPTS:4}",
            backoff = @Backoff(
                    delayExpression = "${AGRICORE_KAFKA_RETRY_DELAY_MS:1000}",
                    multiplierExpression = "${AGRICORE_KAFKA_RETRY_MULTIPLIER:2}",
                    maxDelayExpression = "${AGRICORE_KAFKA_RETRY_MAX_DELAY_MS:8000}"
            ),
            timeout = "${AGRICORE_KAFKA_RETRY_TIMEOUT_MS:30000}",
            dltTopicSuffix = ".DLT",
            exclude = IllegalArgumentException.class,
            autoCreateTopics = "${AGRICORE_KAFKA_RETRY_AUTO_CREATE_TOPICS:false}"
    )
    @KafkaListener(
            topics = {
                    "${agricore.kafka.topics.sales-events:agricore.sales.events}",
                    "${agricore.kafka.topics.traceability-events:agricore.traceability.events}",
                    "${agricore.kafka.topics.iot-events:agricore.iot.events}"
            },
            groupId = "${agricore.kafka.consumer.group-id:notification-service}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String raw = record.value();
        JsonNode root = readRoot(raw);
        DomainEventEnvelopeReader.Envelope envelope = DomainEventEnvelopeReader.read(objectMapper, raw);
        if (!SUPPORTED_EVENTS.contains(envelope.eventType()) || envelope.eventVersion() != 1) {
            throw new IllegalArgumentException("Unsupported notification event: " + envelope.eventType());
        }
        eventSourcePolicy.validate(record.topic(), envelope);
        notificationService.consume(toCommand(root, envelope));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Notification event routed to DLT topic={} partition={} offset={} exceptionType={}",
                record.topic(), record.partition(), record.offset(), exception.getClass().getSimpleName());
    }

    private NotificationEventCommand toCommand(
            JsonNode root,
            DomainEventEnvelopeReader.Envelope envelope
    ) {
        JsonNode payload = envelope.payload();
        String correlationId = textOrNull(root.path("correlationId"), 100);
        return switch (envelope.eventType()) {
            case EventTypes.SALES_ORDER_CONFIRMED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    requiredUuid(payload, "customerId"),
                    "Sales order " + required(payload, "orderNumber", 64) + " confirmed",
                    "Sales order " + required(payload, "orderNumber", 64) + " is confirmed and inventory is committed.",
                    correlationId
            );
            case EventTypes.SALES_ORDER_CANCELLED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    requiredUuid(payload, "customerId"),
                    "Sales order " + required(payload, "orderNumber", 64) + " cancelled",
                    "Sales order " + required(payload, "orderNumber", 64) + " ended with status "
                            + required(payload, "finalStatus", 32) + ".",
                    correlationId
            );
            case EventTypes.TRACEABILITY_CODE_GENERATED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP", "operations",
                    "Traceability code generated",
                    "Traceability code " + required(payload, "traceabilityCode", 128)
                            + " is available at " + required(payload, "publicUrl", 2048),
                    correlationId
            );
            case EventTypes.SENSOR_THRESHOLD_EXCEEDED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    prefixed(payload, "plotId", "operations", "plot:"),
                    "Sensor threshold exceeded",
                    "Metric " + required(payload, "metricType", 64) + " exceeded its configured threshold.",
                    correlationId
            );
            case EventTypes.DEVICE_OFFLINE_DETECTED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    prefixed(payload, "plotId", "operations", "plot:"),
                    "IoT device offline",
                    "Device " + required(payload, "deviceCode", 64) + " is offline.",
                    correlationId
            );
            default -> throw new IllegalArgumentException("Unsupported notification event: " + envelope.eventType());
        };
    }

    private JsonNode readRoot(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Notification event must be a JSON object");
            }
            return root;
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Notification event must be valid JSON", exception);
        }
    }

    private static String required(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maxLength) {
            throw new IllegalArgumentException("Notification event payload requires " + field);
        }
        return value.textValue().trim();
    }

    private static String requiredUuid(JsonNode payload, String field) {
        String value = required(payload, field, 36);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Notification event payload requires UUID " + field, exception);
        }
    }

    private static String prefixed(JsonNode payload, String field, String fallback, String prefix) {
        JsonNode value = payload.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            return fallback;
        }
        try {
            return prefix + UUID.fromString(value.textValue().trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Notification event payload requires UUID " + field, exception);
        }
    }

    private static String textOrNull(JsonNode value, int maxLength) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        if (value.textValue().length() > maxLength) {
            throw new IllegalArgumentException("Notification event correlationId is too long");
        }
        return value.textValue().trim();
    }
}
