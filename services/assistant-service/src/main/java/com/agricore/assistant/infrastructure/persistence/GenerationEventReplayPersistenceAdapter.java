package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.port.GenerationEventReplayRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GenerationEventReplayPersistenceAdapter implements GenerationEventReplayRepository {

    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;

    public GenerationEventReplayPersistenceAdapter(
            ChatGenerationJpaRepository generationRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper
    ) {
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<GenerationEventReplayBatch> findOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            long afterSequence,
            int limit,
            Instant now
    ) {
        if (afterSequence < -1 || limit < 1 || limit > 1000 || now == null) {
            throw new IllegalArgumentException("invalid event cursor, limit or clock");
        }
        return generationRepository.findOwnedForReplay(
                        generationId, conversationId, ownerUserId)
                .map(generation -> GenerationEventReplayBatch.validated(
                        eventRepository.findAfter(
                                        generationId, afterSequence, now, PageRequest.of(0, limit))
                                .stream()
                                .map(mapper::toDomain)
                                .toList(),
                        generation.getNextEventSequence(),
                        generation.getStatus().terminal(),
                        afterSequence
                ));
    }
}
