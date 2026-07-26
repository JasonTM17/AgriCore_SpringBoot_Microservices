package com.agricore.traceability.infrastructure.messaging;

import com.agricore.common.event.DomainEventEnvelopeReader;
import com.agricore.common.event.EventTypes;
import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
final class HarvestCompletedEventParser {

    private static final String PRODUCER = "harvest-service";
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "harvestId",
            "harvestBatchId",
            "farmId",
            "cropCycleId",
            "plotId",
            "warehouseId",
            "productCode",
            "grossWeightKg",
            "netWeightKg",
            "qualityGrade",
            "harvestDate",
            "farmName",
            "plotCode",
            "productName",
            "careSummary"
    );

    private final ObjectMapper objectMapper;

    HarvestCompletedEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Optional<CreateTraceabilityRequest> parse(String raw) {
        DomainEventEnvelopeReader.Envelope envelope = DomainEventEnvelopeReader.read(objectMapper, raw);
        if (!EventTypes.HARVEST_COMPLETED.equals(envelope.eventType())) {
            rejectUnsupportedHarvestVersion(envelope.eventType());
            return Optional.empty();
        }
        if (envelope.eventVersion() != 1) {
            throw invalid("eventVersion must be 1 for HarvestCompleted.v1");
        }
        if (!PRODUCER.equals(envelope.producer())) {
            throw invalid("producer must be harvest-service");
        }

        JsonNode payload = envelope.payload();
        rejectUnknownFields(payload);
        UUID harvestId = requiredUuid(payload, "harvestId");
        UUID harvestBatchId = requiredUuid(payload, "harvestBatchId");
        if (!harvestId.equals(harvestBatchId)) {
            throw invalid("harvestId and harvestBatchId must match");
        }
        requiredUuid(payload, "farmId");
        UUID cropCycleId = requiredUuid(payload, "cropCycleId");
        UUID plotId = requiredUuid(payload, "plotId");
        requiredUuid(payload, "warehouseId");
        String productCode = requiredText(payload, "productCode", 64);
        BigDecimal grossWeightKg = positiveDecimal(payload, "grossWeightKg");
        BigDecimal netWeightKg = positiveDecimal(payload, "netWeightKg");
        String qualityGrade = requiredText(payload, "qualityGrade", 32);
        LocalDate harvestDate = requiredDate(payload, "harvestDate");
        String productName = requiredText(payload, "productName", 200);

        String farmName = optionalText(payload, "farmName", 200).orElse(null);
        String plotCode = optionalText(payload, "plotCode", 64).orElse(null);
        String careSummary = optionalText(payload, "careSummary", 1000).orElse(null);

        return Optional.of(new CreateTraceabilityRequest(
                envelope.eventId(),
                harvestBatchId,
                cropCycleId,
                plotId,
                farmName,
                plotCode,
                productName,
                null,
                null,
                harvestDate,
                qualityGrade,
                netWeightKg,
                careSummary,
                productCode,
                grossWeightKg
        ));
    }

    private static void rejectUnsupportedHarvestVersion(String eventType) {
        if (eventType.equals("HarvestCompleted") || eventType.startsWith("HarvestCompleted.")) {
            throw invalid("unsupported HarvestCompleted event type " + eventType);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        return requiredText(payload, field, Integer.MAX_VALUE);
    }

    private static String requiredText(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid("payload." + field + " must be a non-blank string");
        }
        if (value.textValue().length() > maxLength) {
            throw invalid("payload." + field + " must be at most " + maxLength + " characters");
        }
        return value.textValue();
    }

    private static Optional<String> optionalText(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.get(field);
        if (value == null) {
            return Optional.empty();
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid("payload." + field + " must be a non-blank string when present");
        }
        if (value.textValue().length() > maxLength) {
            throw invalid("payload." + field + " must be at most " + maxLength + " characters");
        }
        return Optional.of(value.textValue());
    }

    private static void rejectUnknownFields(JsonNode payload) {
        Iterator<String> fields = payload.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!PAYLOAD_FIELDS.contains(field)) {
                throw invalid("unknown payload field " + field);
            }
        }
    }

    private static UUID requiredUuid(JsonNode payload, String field) {
        String value = requiredText(payload, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw invalid("payload." + field + " must be a UUID", ex);
        }
    }

    private static BigDecimal positiveDecimal(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isNumber()) {
            throw invalid("payload." + field + " must be a number");
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("payload." + field + " must be greater than zero");
        }
        return decimal;
    }

    private static LocalDate requiredDate(JsonNode payload, String field) {
        String value = requiredText(payload, field);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalid("payload." + field + " must be an ISO-8601 date", ex);
        }
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid HarvestCompleted.v1 event: " + reason);
    }

    private static IllegalArgumentException invalid(String reason, Exception cause) {
        return new IllegalArgumentException("Invalid HarvestCompleted.v1 event: " + reason, cause);
    }
}
