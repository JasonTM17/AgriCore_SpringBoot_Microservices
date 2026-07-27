package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GenerationEventReplayRepository {

    Optional<GenerationEventReplayBatch> findOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            long afterSequence,
            int limit,
            Instant now
    );
}
