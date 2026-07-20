package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.GenerationCancelResult;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class GenerationCancellationTransitionStore {

    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;
    private final GenerationEventFactory eventFactory;
    private final GenerationEventPayloadCodec payloadCodec;

    public GenerationCancellationTransitionStore(
            ChatGenerationJpaRepository generationRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper,
            GenerationEventFactory eventFactory,
            GenerationEventPayloadCodec payloadCodec
    ) {
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.eventFactory = eventFactory;
        this.payloadCodec = payloadCodec;
    }

    @Transactional
    public GenerationCancelResult request(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            Instant requestedAt,
            Instant eventExpiresAt
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(conversationId, "conversationId is required");
        Objects.requireNonNull(ownerUserId, "ownerUserId is required");
        GenerationTransitionTime.requireEventWindow(requestedAt, eventExpiresAt);
        ChatGenerationEntity generation = generationRepository.findOwnedForUpdate(
                        generationId, conversationId, ownerUserId)
                .orElseThrow(AssistantException::generationNotFound);

        if (generation.getStatus() == GenerationStatus.QUEUED) {
            markCancelled(generation, requestedAt);
            eventRepository.save(eventFactory.create(
                    generation, GenerationEventType.CANCELLED, payloadCodec.cancelled(),
                    requestedAt, eventExpiresAt));
            generationRepository.flush();
            return result(generation, true, false);
        }
        if (generation.getStatus() == GenerationStatus.RUNNING) {
            generation.setStatus(GenerationStatus.CANCEL_REQUESTED);
            generation.setCancelRequestedAt(requestedAt);
            generation.setUpdatedAt(requestedAt);
            eventRepository.save(eventFactory.create(
                    generation, GenerationEventType.STATUS,
                    payloadCodec.status(GenerationStatus.CANCEL_REQUESTED),
                    requestedAt, eventExpiresAt));
            generationRepository.flush();
            return result(generation, true, true);
        }
        return result(
                generation,
                false,
                generation.getStatus() == GenerationStatus.CANCEL_REQUESTED
        );
    }

    @Transactional
    public Optional<AssistantGeneration> finish(
            UUID generationId,
            UUID leaseToken,
            Instant cancelledAt,
            Instant eventExpiresAt
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        GenerationTransitionTime.requireEventWindow(cancelledAt, eventExpiresAt);
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        if (generation == null
                || generation.getStatus() != GenerationStatus.CANCEL_REQUESTED
                || !leaseToken.equals(generation.getLeaseToken())) {
            return Optional.empty();
        }

        markCancelled(generation, cancelledAt);
        eventRepository.save(eventFactory.create(
                generation, GenerationEventType.CANCELLED, payloadCodec.cancelled(),
                cancelledAt, eventExpiresAt));
        generationRepository.flush();
        return Optional.of(mapper.toDomain(generation));
    }

    private GenerationCancelResult result(
            ChatGenerationEntity generation,
            boolean changed,
            boolean workerCancellationRequired
    ) {
        return new GenerationCancelResult(
                mapper.toDomain(generation), changed, workerCancellationRequired);
    }

    private static void markCancelled(ChatGenerationEntity generation, Instant cancelledAt) {
        generation.setStatus(GenerationStatus.CANCELLED);
        generation.setActiveConversationId(null);
        generation.setErrorCode(null);
        generation.setLeaseToken(null);
        generation.setLeaseExpiresAt(null);
        generation.setCancelledAt(cancelledAt);
        generation.setCompletedAt(cancelledAt);
        generation.setUpdatedAt(cancelledAt);
        generation.setProviderLatencyMs(GenerationTransitionTime.elapsedMillisIfStarted(
                generation.getStartedAt(), cancelledAt));
        generation.setTotalLatencyMs(GenerationTransitionTime.elapsedMillis(
                generation.getQueuedAt(), cancelledAt));
    }
}
