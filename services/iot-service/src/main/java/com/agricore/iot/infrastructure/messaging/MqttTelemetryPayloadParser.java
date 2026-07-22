package com.agricore.iot.infrastructure.messaging;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class MqttTelemetryPayloadParser {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "readingId", "deviceCode", "metricType", "metricValue", "unit", "recordedAt"
    );
    private static final Pattern SAFE_DEVICE_CODE = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Duration DEFAULT_MAX_READING_AGE = Duration.ofDays(30);
    private static final Duration DEFAULT_MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Duration maxReadingAge;
    private final Duration maxFutureSkew;

    public MqttTelemetryPayloadParser(ObjectMapper objectMapper, Validator validator) {
        this(objectMapper, validator, DEFAULT_MAX_READING_AGE, DEFAULT_MAX_FUTURE_SKEW);
    }

    @Autowired
    public MqttTelemetryPayloadParser(
            ObjectMapper objectMapper,
            Validator validator,
            @Value("${agricore.mqtt.max-reading-age:P30D}") Duration maxReadingAge,
            @Value("${agricore.mqtt.max-future-skew:PT5M}") Duration maxFutureSkew
    ) {
        if (maxReadingAge.isNegative() || maxReadingAge.isZero()
                || maxReadingAge.compareTo(Duration.ofDays(365)) > 0
                || maxFutureSkew.isNegative() || maxFutureSkew.isZero()
                || maxFutureSkew.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("MQTT timestamp windows are outside safe bounds");
        }
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.maxReadingAge = maxReadingAge;
        this.maxFutureSkew = maxFutureSkew;
    }

    public IngestReadingRequest parse(String topic, byte[] payloadBytes) throws Exception {
        String topicDeviceCode = deviceCodeFromTopic(topic);
        JsonNode payload;
        try (JsonParser parser = objectMapper.getFactory().createParser(payloadBytes)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            payload = objectMapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("MQTT telemetry contains trailing JSON tokens");
            }
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("MQTT telemetry payload must be a JSON object");
        }
        payload.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown MQTT telemetry field");
            }
        });

        IngestReadingRequest request = new IngestReadingRequest(
                requiredDeviceCode(payload, topicDeviceCode),
                requiredText(payload, "metricType", 64),
                requiredDecimal(payload, "metricValue"),
                requiredText(payload, "unit", 16),
                optionalInstant(payload, "recordedAt"),
                requiredUuid(payload, "readingId")
        );
        Set<ConstraintViolation<IngestReadingRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("MQTT telemetry validation failed");
        }
        return request;
    }

    private static String deviceCodeFromTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length != 4 || !"agricore".equals(parts[0]) || !"telemetry".equals(parts[1])
                || !"reading".equals(parts[3]) || parts[2].isBlank() || parts[2].length() > 64
                || !SAFE_DEVICE_CODE.matcher(parts[2]).matches()) {
            throw new IllegalArgumentException("MQTT topic does not match telemetry contract");
        }
        return parts[2];
    }

    private static String requiredDeviceCode(JsonNode payload, String topicDeviceCode) {
        String payloadDeviceCode = requiredText(payload, "deviceCode", 64);
        if (!payloadDeviceCode.equalsIgnoreCase(topicDeviceCode)) {
            throw new IllegalArgumentException("MQTT payload deviceCode does not match topic");
        }
        return payloadDeviceCode;
    }

    private static String requiredText(JsonNode payload, String field, int maxLength) {
        JsonNode value = payload.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maxLength) {
            throw new IllegalArgumentException("Missing or invalid MQTT text field");
        }
        return value.textValue().trim();
    }

    private static UUID requiredUuid(JsonNode payload, String field) {
        try {
            return UUID.fromString(requiredText(payload, field, 36));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid MQTT UUID field", exception);
        }
    }

    private static BigDecimal requiredDecimal(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException("Invalid MQTT numeric field");
        }
        return value.decimalValue();
    }

    private Instant optionalInstant(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Invalid MQTT timestamp field");
        }
        try {
            Instant recordedAt = Instant.parse(value.textValue());
            Instant now = Instant.now();
            if (recordedAt.isBefore(now.minus(maxReadingAge))
                    || recordedAt.isAfter(now.plus(maxFutureSkew))) {
                throw new IllegalArgumentException("MQTT timestamp is outside the accepted freshness window");
            }
            return recordedAt;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MQTT timestamp field", exception);
        }
    }
}
