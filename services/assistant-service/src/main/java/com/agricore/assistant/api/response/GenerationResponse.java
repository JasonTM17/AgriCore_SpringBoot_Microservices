package com.agricore.assistant.api.response;

import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.GenerationStatus;

import java.time.Instant;
import java.util.UUID;

public record GenerationResponse(
        UUID id,
        UUID conversationId,
        GenerationStatus status,
        String provider,
        String model,
        String errorCode,
        UUID userMessageId,
        long nextEventSequence,
        Instant queuedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        boolean deduplicated
) {
    public static GenerationResponse from(GenerationSubmissionResult result) {
        return from(result.generation(), result.userMessage(), result.deduplicated());
    }

    public static GenerationResponse from(AssistantGeneration generation) {
        return from(generation, null, false);
    }

    private static GenerationResponse from(
            AssistantGeneration generation,
            AssistantMessage userMessage,
            boolean deduplicated
    ) {
        return new GenerationResponse(
                generation.id(), generation.conversationId(), generation.status(),
                generation.provider(), generation.model(), generation.errorCode(),
                userMessage == null ? null : userMessage.id(), generation.nextEventSequence(),
                generation.queuedAt(), generation.createdAt(), generation.updatedAt(),
                generation.completedAt(), deduplicated
        );
    }
}
