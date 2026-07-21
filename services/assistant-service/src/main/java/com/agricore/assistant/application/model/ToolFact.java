package com.agricore.assistant.application.model;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ToolFact(
        String citationId,
        ToolSource source,
        Map<String, String> fields
) {

    private static final int MAX_FIELDS = 8;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final String CITATION_PATTERN = "[A-Z][A-Z0-9-]{0,31}";
    private static final String FIELD_PATTERN = "[a-z][a-zA-Z0-9]{0,31}";

    public ToolFact {
        citationId = requirePattern(citationId, "citationId", CITATION_PATTERN);
        source = Objects.requireNonNull(source, "source is required");
        if (fields == null || fields.isEmpty() || fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("tool fact must contain between 1 and 8 fields");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            String normalizedKey = requirePattern(key, "field name", FIELD_PATTERN);
            if (normalized.put(normalizedKey, normalizeValue(value)) != null) {
                throw new IllegalArgumentException("tool field names must be unique after normalization");
            }
        });
        fields = Collections.unmodifiableMap(normalized);
    }

    private static String requirePattern(String value, String field, String pattern) {
        String normalized = value == null ? "" : value.strip();
        if (!normalized.matches(pattern)) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return normalized;
    }

    private static String normalizeValue(String value) {
        Objects.requireNonNull(value, "tool field value is required");
        String unicode = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder safe = new StringBuilder(unicode.length());
        unicode.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT) {
                safe.append(' ');
            } else {
                safe.appendCodePoint(codePoint);
            }
        });
        String normalized = safe.toString().strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("tool field value must be between 1 and 256 characters");
        }
        return normalized;
    }
}
