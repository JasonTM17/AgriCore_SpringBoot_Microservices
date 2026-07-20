package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GenerationRepository {

    Optional<GenerationSubmissionResult> findIdempotent(
            UUID conversationId,
            UUID ownerUserId,
            String idempotencyKey,
            String requestHash
    );

    GenerationSubmissionResult submit(GenerationSubmissionCommand command);

    Optional<AssistantGeneration> findOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId
    );

    List<AssistantGenerationEvent> findEventsOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            long afterSequence,
            int limit,
            Instant now
    );
}
