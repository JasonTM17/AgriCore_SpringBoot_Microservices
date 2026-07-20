package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.domain.model.GenerationStatus;

import java.util.UUID;

public record GenerationExecutionReference(
        UUID conversationId,
        UUID ownerUserId,
        GenerationStatus status,
        UUID leaseToken
) {
}
