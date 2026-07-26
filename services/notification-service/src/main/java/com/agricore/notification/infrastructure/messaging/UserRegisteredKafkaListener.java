package com.agricore.notification.infrastructure.messaging;

import com.agricore.notification.api.request.UserRegisteredCommand;
import com.agricore.notification.application.service.NotificationApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka consumer for UserRegistered.v1 → welcome notification
 * (idempotent via processed_events).
 *
 * <p>NotificationRequested.v1 is not consumed here: no service produces it today.
 */
@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class UserRegisteredKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredKafkaListener.class);

    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;

    public UserRegisteredKafkaListener(
            NotificationApplicationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${agricore.kafka.topics.identity-events:agricore.identity.events}",
            groupId = "${agricore.kafka.consumer.group-id:notification-service}"
    )
    public void onMessage(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String eventType = text(root, "eventType");
            if (eventType == null || !eventType.contains("UserRegistered")) {
                return;
            }
            String eventId = text(root, "eventId");
            JsonNode payload = root.get("payload");
            if (eventId == null || payload == null) {
                log.warn("Ignoring identity event without eventId/payload");
                return;
            }

            UserRegisteredCommand command = new UserRegisteredCommand(
                    eventId,
                    text(payload, "userId"),
                    text(payload, "email"),
                    text(payload, "fullName"),
                    roles(payload.get("roles"))
            );
            notificationService.recordUserRegistered(command);
            log.info("Processed UserRegistered eventId={}", eventId);
        } catch (Exception ex) {
            log.error("Failed to process identity event: {}", ex.getMessage());
            throw new IllegalStateException("Identity event processing failed", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<String> roles(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>(node.size());
        node.forEach(item -> codes.add(item.asText()));
        return List.copyOf(codes);
    }
}
