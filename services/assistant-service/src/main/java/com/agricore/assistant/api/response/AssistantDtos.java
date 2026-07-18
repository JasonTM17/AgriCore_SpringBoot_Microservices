package com.agricore.assistant.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record CapabilitiesResponse(
            String provider,
            boolean generationAvailable,
            boolean streaming,
            List<String> tools,
            String reason
    ) {
    }

    public record ConversationResponse(
            UUID id,
            String title,
            UUID farmId,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MessageResponse(
            UUID id,
            String role,
            String content,
            Instant createdAt,
            UUID generationId
    ) {
    }

    public record StartGenerationResponse(
            UUID generationId,
            String status
    ) {
    }

    public record CreateConversationRequest(
            String title,
            UUID farmId
    ) {
    }

    public record StartGenerationRequest(
            @jakarta.validation.constraints.NotBlank String content,
            @jakarta.validation.constraints.NotBlank String idempotencyKey
    ) {
    }
}
