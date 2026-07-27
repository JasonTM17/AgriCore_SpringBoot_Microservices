package com.agricore.harvest.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
final class HarvestCompletionEventValidator {

    private static final String EXPECTED_TOPIC = "agricore.harvest.events";
    private static final String EXPECTED_PRODUCER = "harvest-service";
    private static final int EXPECTED_VERSION = 1;

    private final ObjectMapper objectMapper;

    HarvestCompletionEventValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void requireValid(
            OutboxEventEntity event,
            HarvestBatchEntity harvest,
            UUID eventId
    ) {
        JsonNode envelope = readEnvelope(event);
        JsonNode payload = envelope.get("payload");

        require(EXPECTED_TOPIC.equals(event.getTopic()));
        require(eventId.equals(uuid(envelope, "eventId")));
        require(EventTypes.HARVEST_COMPLETED.equals(text(envelope, "eventType")));
        require(envelope.path("eventVersion").isInt());
        require(envelope.path("eventVersion").intValue() == EXPECTED_VERSION);
        require(EXPECTED_PRODUCER.equals(text(envelope, "producer")));
        requireInstant(envelope, "occurredAt");
        require(payload != null && payload.isObject());

        require(harvest.getId().equals(uuid(payload, "harvestId")));
        require(harvest.getId().equals(uuid(payload, "harvestBatchId")));
        require(harvest.getFarmId() != null);
        require(harvest.getFarmId().equals(uuid(payload, "farmId")));
        require(harvest.getCropCycleId().equals(uuid(payload, "cropCycleId")));
        require(harvest.getPlotId().equals(uuid(payload, "plotId")));
        require(harvest.getWarehouseId().equals(uuid(payload, "warehouseId")));
        require(harvest.getProductCode().equals(text(payload, "productCode")));
        require(equalDecimal(payload, "grossWeightKg", harvest.getGrossWeightKg()));
        require(equalDecimal(payload, "netWeightKg", harvest.getNetWeightKg()));
        require(harvest.getQualityGrade().equals(text(payload, "qualityGrade")));
        require(harvest.getHarvestedAt().toString().substring(0, 10)
                .equals(text(payload, "harvestDate")));
        requireNonBlank(payload, "productName");
        requireOptionalNonBlank(payload, "farmName");
        requireOptionalNonBlank(payload, "plotCode");
        requireOptionalNonBlank(payload, "careSummary");
    }

    private JsonNode readEnvelope(OutboxEventEntity event) {
        try {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            require(envelope != null && envelope.isObject());
            return envelope;
        } catch (HarvestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid();
        }
        return value.textValue();
    }

    private static void requireInstant(JsonNode node, String field) {
        try {
            Instant.parse(text(node, field));
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static boolean equalDecimal(JsonNode node, String field, BigDecimal expected) {
        JsonNode value = node.get(field);
        return value != null
                && value.isNumber()
                && value.decimalValue().compareTo(expected) == 0;
    }

    private static void requireNonBlank(JsonNode node, String field) {
        text(node, field);
    }

    private static void requireOptionalNonBlank(JsonNode node, String field) {
        if (node.has(field)) {
            requireNonBlank(node, field);
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private static HarvestException invalid() {
        return new HarvestException(
                "OUTBOX_EVENT_INVALID",
                "Harvest completion event payload is invalid",
                409
        );
    }
}
