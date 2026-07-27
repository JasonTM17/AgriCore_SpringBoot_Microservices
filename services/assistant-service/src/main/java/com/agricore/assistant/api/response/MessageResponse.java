package com.agricore.assistant.api.response;

import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID generationId,
        long sequenceNo,
        MessageRole role,
        String content,
        Long tokenCount,
        Instant createdAt
) {
    public static MessageResponse from(AssistantMessage message) {
        return new MessageResponse(
                message.id(),
                message.conversationId(),
                message.generationId(),
                message.sequenceNo(),
                message.role(),
                message.content(),
                message.tokenCount(),
                message.createdAt()
        );
    }
}
