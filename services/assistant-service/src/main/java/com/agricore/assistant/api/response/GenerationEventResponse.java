package com.agricore.assistant.api.response;

import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.GenerationEventType;

import java.time.Instant;
import java.util.UUID;

public record GenerationEventResponse(
        UUID id,
        UUID generationId,
        long sequenceNo,
        GenerationEventType eventType,
        String payload,
        Instant createdAt
) {
    public static GenerationEventResponse from(AssistantGenerationEvent event) {
        return new GenerationEventResponse(
                event.id(), event.generationId(), event.sequenceNo(), event.eventType(),
                event.payload(), event.createdAt()
        );
    }
}
