package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationCancelResult;
import com.agricore.assistant.application.model.GenerationCompletion;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.GenerationLeaseStatus;
import com.agricore.assistant.domain.model.AssistantGeneration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationExecutionRepository {

    Optional<GenerationExecutionContext> claim(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    );

    DeltaAppendResult appendDelta(
            UUID generationId,
            UUID leaseToken,
            String delta,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    );

    GenerationLeaseStatus renewLease(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt
    );

    Optional<AssistantGeneration> complete(
            UUID generationId,
            UUID leaseToken,
            GenerationCompletion completion
    );

    Optional<AssistantGeneration> fail(
            UUID generationId,
            UUID leaseToken,
            String errorCode,
            Instant failedAt,
            Instant eventExpiresAt
    );

    GenerationCancelResult requestCancellation(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            Instant requestedAt,
            Instant eventExpiresAt
    );

    Optional<AssistantGeneration> finishCancellation(
            UUID generationId,
            UUID leaseToken,
            Instant cancelledAt,
            Instant eventExpiresAt
    );

    List<UUID> findQueuedGenerationIds(int limit);

    int expireLeases(Instant now, Instant eventExpiresAt, int limit);
}
