package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.domain.model.GenerationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class GenerationEventPayloadCodec {

    private static final int MAX_PAYLOAD_LENGTH = 65_536;

    private final ObjectMapper objectMapper;

    public GenerationEventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String status(GenerationStatus status) {
        return encode(Map.of("status", Objects.requireNonNull(status, "status is required").name()));
    }

    public String delta(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("delta is required");
        }
        return encode(Map.of("delta", value));
    }

    public String completed(
            UUID assistantMessageId,
            String finishReason,
            Integer inputTokens,
            Integer outputTokens
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", GenerationStatus.COMPLETED.name());
        payload.put("assistantMessageId", Objects.requireNonNull(
                assistantMessageId, "assistantMessageId is required"));
        payload.put("finishReason", Objects.requireNonNull(finishReason, "finishReason is required"));
        if (inputTokens != null) {
            payload.put("inputTokens", inputTokens);
        }
        if (outputTokens != null) {
            payload.put("outputTokens", outputTokens);
        }
        return encode(payload);
    }

    public String error(String errorCode) {
        return encode(Map.of(
                "status", GenerationStatus.FAILED.name(),
                "errorCode", Objects.requireNonNull(errorCode, "errorCode is required")
        ));
    }

    public String cancelled() {
        return status(GenerationStatus.CANCELLED);
    }

    private String encode(Map<String, Object> payload) {
        try {
            String encoded = objectMapper.writeValueAsString(payload);
            if (encoded.length() > MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException("generation event payload is too large");
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("generation event payload cannot be encoded", exception);
        }
    }
}
