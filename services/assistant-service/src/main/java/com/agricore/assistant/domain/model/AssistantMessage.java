package com.agricore.assistant.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AssistantMessage(
        UUID id,
        UUID conversationId,
        UUID generationId,
        long sequenceNo,
        MessageRole role,
        String content,
        Long tokenCount,
        Instant createdAt
) {
}
