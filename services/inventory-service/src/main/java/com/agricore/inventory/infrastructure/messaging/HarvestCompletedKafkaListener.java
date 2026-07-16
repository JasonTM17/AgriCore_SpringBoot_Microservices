package com.agricore.inventory.infrastructure.messaging;

import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka consumer for HarvestCompleted.v1 → stock-in (idempotent via processed_events).
 */
@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class HarvestCompletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestCompletedKafkaListener.class);

    private final InventoryApplicationService inventoryService;
    private final ObjectMapper objectMapper;

    public HarvestCompletedKafkaListener(InventoryApplicationService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${agricore.kafka.topics.harvest-events:agricore.harvest.events}",
            groupId = "${agricore.kafka.consumer.group-id:inventory-service}"
    )
    public void onMessage(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String eventType = text(root, "eventType");
            if (eventType == null || !eventType.contains("HarvestCompleted")) {
                return;
            }
            String eventId = text(root, "eventId");
            JsonNode payload = root.get("payload");
            if (eventId == null || payload == null) {
                log.warn("Ignoring harvest event without eventId/payload");
                return;
            }

            HarvestCompletedCommand command = new HarvestCompletedCommand(
                    eventId,
                    UUID.fromString(text(payload, "harvestBatchId")),
                    UUID.fromString(text(payload, "warehouseId")),
                    text(payload, "productCode"),
                    new BigDecimal(text(payload, "netWeightKg")),
                    text(payload, "qualityGrade")
            );
            inventoryService.processHarvestCompleted(command);
            log.info("Processed HarvestCompleted eventId={}", eventId);
        } catch (Exception ex) {
            log.error("Failed to process harvest event: {}", ex.getMessage());
            throw new IllegalStateException("Harvest event processing failed", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
