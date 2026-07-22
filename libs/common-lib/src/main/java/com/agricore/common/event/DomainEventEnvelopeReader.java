package com.agricore.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Strict reader for the metadata shared by all versioned domain-event envelopes.
 * Payload validation remains owned by each event consumer.
 */
public final class DomainEventEnvelopeReader {

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId",
            "eventType",
            "eventVersion",
            "occurredAt",
            "traceId",
            "correlationId",
            "causationId",
            "producer",
            "payload"
    );

    private DomainEventEnvelopeReader() {
    }

    public static Envelope read(ObjectMapper objectMapper, String raw) {
        Objects.requireNonNull(objectMapper, "objectMapper is required");
        if (raw == null || raw.isBlank()) {
            throw invalid("message must not be blank");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw invalid("message must be valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw invalid("message root must be an object");
        }
        rejectUnknownFields(root);

        UUID eventId = uuid(root, "eventId");
        String eventType = requiredText(root, "eventType");
        int eventVersion = positiveInteger(root, "eventVersion");
        Instant occurredAt = instant(root, "occurredAt");
        optionalTextOrNull(root, "traceId");
        optionalTextOrNull(root, "correlationId");
        optionalTextOrNull(root, "causationId");
        String producer = requiredText(root, "producer");
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) {
            throw invalid("payload must be an object");
        }

        return new Envelope(
                eventId,
                eventType,
                eventVersion,
                occurredAt,
                producer,
                payload.deepCopy()
        );
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private static void optionalTextOrNull(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull() && !value.isTextual()) {
            throw invalid(field + " must be a string or null when present");
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!ENVELOPE_FIELDS.contains(field)) {
                throw invalid("unknown field " + field);
            }
        }
    }

    private static UUID uuid(JsonNode root, String field) {
        String value = requiredText(root, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw invalid(field + " must be a UUID", ex);
        }
    }

    private static int positiveInteger(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) {
            throw invalid(field + " must be a positive integer");
        }
        return value.intValue();
    }

    private static Instant instant(JsonNode root, String field) {
        String value = requiredText(root, field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalid(field + " must be an ISO-8601 instant", ex);
        }
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid domain event envelope: " + reason);
    }

    private static IllegalArgumentException invalid(String reason, Exception cause) {
        return new IllegalArgumentException("Invalid domain event envelope: " + reason, cause);
    }

    public record Envelope(
            UUID eventId,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            String producer,
            JsonNode payload
    ) {
    }
}
