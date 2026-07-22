package com.agricore.notification.infrastructure.messaging;

import com.agricore.common.event.DomainEventEnvelopeReader;
import com.agricore.common.event.EventTypes;
import com.agricore.notification.application.service.NotificationApplicationService;
import com.agricore.notification.application.service.NotificationEventCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            EventTypes.SALES_ORDER_CONFIRMED,
            EventTypes.SALES_ORDER_CANCELLED,
            EventTypes.TRACEABILITY_CODE_GENERATED,
            EventTypes.SENSOR_THRESHOLD_EXCEEDED,
            EventTypes.DEVICE_OFFLINE_DETECTED
    );

    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(
            NotificationApplicationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "${agricore.kafka.topics.sales-events:agricore.sales.events}",
                    "${agricore.kafka.topics.traceability-events:agricore.traceability.events}",
                    "${agricore.kafka.topics.iot-events:agricore.iot.events}"
            },
            groupId = "${agricore.kafka.consumer.group-id:notification-service}"
    )
    public void onMessage(String raw) {
        JsonNode root = readRoot(raw);
        DomainEventEnvelopeReader.Envelope envelope = DomainEventEnvelopeReader.read(objectMapper, raw);
        if (!SUPPORTED_EVENTS.contains(envelope.eventType()) || envelope.eventVersion() != 1) {
            throw new IllegalArgumentException("Unsupported notification event: " + envelope.eventType());
        }
        notificationService.consume(toCommand(root, envelope));
    }

    private NotificationEventCommand toCommand(
            JsonNode root,
            DomainEventEnvelopeReader.Envelope envelope
    ) {
        JsonNode payload = envelope.payload();
        String correlationId = textOrNull(root.path("correlationId"));
        return switch (envelope.eventType()) {
            case EventTypes.SALES_ORDER_CONFIRMED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    required(payload, "customerId"),
                    "Sales order " + required(payload, "orderNumber") + " confirmed",
                    "Sales order " + required(payload, "orderNumber") + " is confirmed and inventory is committed.",
                    correlationId
            );
            case EventTypes.SALES_ORDER_CANCELLED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    required(payload, "customerId"),
                    "Sales order " + required(payload, "orderNumber") + " cancelled",
                    "Sales order " + required(payload, "orderNumber") + " ended with status "
                            + required(payload, "finalStatus") + ".",
                    correlationId
            );
            case EventTypes.TRACEABILITY_CODE_GENERATED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP", "operations",
                    "Traceability code generated",
                    "Traceability code " + required(payload, "traceabilityCode")
                            + " is available at " + required(payload, "publicUrl"),
                    correlationId
            );
            case EventTypes.SENSOR_THRESHOLD_EXCEEDED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    prefixed(payload, "plotId", "operations", "plot:"),
                    "Sensor threshold exceeded",
                    "Metric " + required(payload, "metricType") + " exceeded its configured threshold.",
                    correlationId
            );
            case EventTypes.DEVICE_OFFLINE_DETECTED -> new NotificationEventCommand(
                    envelope.eventId(), envelope.eventType(), "IN_APP",
                    prefixed(payload, "plotId", "operations", "plot:"),
                    "IoT device offline",
                    "Device " + required(payload, "deviceCode") + " is offline.",
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

    private static String required(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Notification event payload requires " + field);
        }
        return value.textValue();
    }

    private static String prefixed(JsonNode payload, String field, String fallback, String prefix) {
        JsonNode value = payload.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? prefix + value.textValue() : fallback;
    }

    private static String textOrNull(JsonNode value) {
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }
}
