package com.agricore.assistant.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RoleSnapshotJsonCodec {

    private static final TypeReference<List<String>> ROLE_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RoleSnapshotJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to encode assistant role snapshot", ex);
        }
    }

    public List<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("[")) {
            return normalize(List.of(trimmed.split(",")));
        }
        try {
            List<String> roles = objectMapper.readValue(trimmed, ROLE_LIST);
            return normalize(roles);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to decode assistant role snapshot", ex);
        }
    }

    private static List<String> normalize(List<String> roles) {
        return roles == null ? List.of() : roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(role -> !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }
}
