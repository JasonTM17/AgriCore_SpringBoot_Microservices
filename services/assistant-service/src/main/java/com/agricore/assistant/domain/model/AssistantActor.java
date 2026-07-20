package com.agricore.assistant.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record AssistantActor(UUID subject, List<String> roles) {

    public AssistantActor {
        if (subject == null) {
            throw new IllegalArgumentException("subject is required");
        }
        roles = roles == null ? List.of() : roles.stream()
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
