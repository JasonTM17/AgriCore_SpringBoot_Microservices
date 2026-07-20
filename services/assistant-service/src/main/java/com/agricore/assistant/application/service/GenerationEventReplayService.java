package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.port.GenerationEventReplayRepository;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class GenerationEventReplayService {

    private final GenerationEventReplayRepository repository;
    private final Clock clock;

    public GenerationEventReplayService(GenerationEventReplayRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GenerationEventReplayBatch read(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long afterSequence,
            int limit
    ) {
        return repository.findOwned(
                        generationId, conversationId, actor.subject(), afterSequence, limit, clock.instant())
                .orElseThrow(AssistantException::generationNotFound);
    }

    @Transactional(readOnly = true)
    public List<AssistantGenerationEvent> events(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long afterSequence,
            int limit
    ) {
        return read(actor, conversationId, generationId, afterSequence, limit).events();
    }
}
