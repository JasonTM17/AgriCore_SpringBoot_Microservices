package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolEvidenceJsonCodec {

    static final int MAX_JSON_LENGTH = 24_000;

    private final ObjectMapper objectMapper;

    public ToolEvidenceJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public String encode(ToolEvidenceSnapshot evidence) {
        ToolEvidenceSnapshot snapshot = evidence == null ? ToolEvidenceSnapshot.empty() : evidence;
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("facts", snapshot.facts().stream().map(this::factDocument).toList());
        try {
            String json = objectMapper.writeValueAsString(document);
            requireBounded(json);
            return json;
        } catch (JsonProcessingException ex) {
            throw invalid(ex);
        }
    }

    public ToolEvidenceSnapshot decode(String json) {
        requireBounded(json);
        try {
            EvidenceDocument document = objectMapper.readValue(json, EvidenceDocument.class);
            if (document == null || document.facts() == null
                    || document.facts().stream().anyMatch(java.util.Objects::isNull)) {
                throw invalid(null);
            }
            return new ToolEvidenceSnapshot(document.facts().stream()
                    .map(fact -> new ToolFact(fact.citationId(), fact.source(), fact.fields()))
                    .toList());
        } catch (JsonProcessingException | RuntimeException ex) {
            throw invalid(ex);
        }
    }

    private Map<String, Object> factDocument(ToolFact fact) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("citationId", fact.citationId());
        document.put("source", fact.source());
        document.put("fields", fact.fields());
        return document;
    }

    private static void requireBounded(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_LENGTH) {
            throw invalid(null);
        }
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return new IllegalArgumentException("tool evidence JSON is invalid", cause);
    }

    private record EvidenceDocument(List<FactDocument> facts) {
    }

    private record FactDocument(
            String citationId,
            ToolSource source,
            LinkedHashMap<String, String> fields
    ) {
    }
}
