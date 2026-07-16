package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds local traceability read model from HarvestCompleted.v1 (idempotent).
 */
@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class HarvestCompletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestCompletedKafkaListener.class);

    private final TraceabilityApplicationService service;
    private final ObjectMapper objectMapper;

    public HarvestCompletedKafkaListener(TraceabilityApplicationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${agricore.kafka.topics.harvest-events:agricore.harvest.events}",
            groupId = "${agricore.kafka.consumer.group-id:traceability-service}"
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

            CreateTraceabilityRequest request = new CreateTraceabilityRequest(
                    eventId,
                    UUID.fromString(text(payload, "harvestBatchId")),
                    uuidOrNull(payload, "cropCycleId"),
                    uuidOrNull(payload, "plotId"),
                    textOrDefault(payload, "farmName", "Farm"),
                    textOrDefault(payload, "plotCode", "PLOT"),
                    textOrDefault(payload, "productCode", textOrDefault(payload, "productName", "PRODUCT")),
                    text(payload, "varietyName"),
                    dateOrNull(payload, "plantingDate"),
                    dateOrDefault(payload, "harvestDate", LocalDate.now()),
                    text(payload, "qualityGrade"),
                    decimalOrNull(payload, "netWeightKg"),
                    textOrDefault(payload, "careSummary", "See farm records")
            );
            service.createFromHarvest(request);
            log.info("Traceability projection updated for eventId={}", eventId);
        } catch (Exception ex) {
            log.error("Failed to process harvest event for traceability: {}", ex.getMessage());
            throw new IllegalStateException("Traceability harvest event processing failed", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        String v = text(node, field);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String v = text(node, field);
        return v == null ? null : UUID.fromString(v);
    }

    private static LocalDate dateOrNull(JsonNode node, String field) {
        String v = text(node, field);
        return v == null ? null : LocalDate.parse(v);
    }

    private static LocalDate dateOrDefault(JsonNode node, String field, LocalDate fallback) {
        LocalDate d = dateOrNull(node, field);
        return d == null ? fallback : d;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        String v = text(node, field);
        return v == null ? null : new BigDecimal(v);
    }
}
