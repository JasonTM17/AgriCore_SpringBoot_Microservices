package com.agricore.assistant.application.model;

import java.util.Objects;

public record ChatTurn(ChatTurnRole role, String content) {

    private static final int MAX_CONTENT_LENGTH = 200_000;

    public ChatTurn {
        role = Objects.requireNonNull(role, "role is required");
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("chat turn content must be non-blank and at most 200000 characters");
        }
        content = content.strip();
    }
}
