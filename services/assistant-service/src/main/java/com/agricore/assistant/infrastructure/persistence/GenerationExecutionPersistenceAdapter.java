package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationCancelResult;
import com.agricore.assistant.application.model.GenerationCompletion;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationMessageJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class GenerationExecutionPersistenceAdapter implements GenerationExecutionRepository {

    private static final int MAX_HISTORY_MESSAGES = 100;
    private static final int MAX_QUEUE_BATCH = 1_000;

    private final ChatGenerationJpaRepository generationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;
    private final GenerationEventFactory eventFactory;
    private final GenerationEventPayloadCodec payloadCodec;
    private final GenerationTerminalTransitionStore terminalStore;
    private final GenerationCancellationTransitionStore cancellationStore;

    public GenerationExecutionPersistenceAdapter(
            ChatGenerationJpaRepository generationRepository,
            ConversationMessageJpaRepository messageRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper,
            GenerationEventFactory eventFactory,
            GenerationEventPayloadCodec payloadCodec,
            GenerationTerminalTransitionStore terminalStore,
            GenerationCancellationTransitionStore cancellationStore
    ) {
        this.generationRepository = generationRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.eventFactory = eventFactory;
        this.payloadCodec = payloadCodec;
        this.terminalStore = terminalStore;
        this.cancellationStore = cancellationStore;
    }

    @Override
    @Transactional
    public Optional<GenerationExecutionContext> claim(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        GenerationTransitionTime.requireEventWindow(now, leaseExpiresAt);
        GenerationTransitionTime.requireEventWindow(now, eventExpiresAt);
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        if (generation == null || generation.getStatus() != GenerationStatus.QUEUED) {
            return Optional.empty();
        }
        if (generation.getAttemptCount() == Integer.MAX_VALUE) {
            throw new IllegalStateException("generation attempt count is exhausted");
        }

        generation.setStatus(GenerationStatus.RUNNING);
        generation.setStartedAt(now);
        generation.setUpdatedAt(now);
        generation.setLeaseToken(leaseToken);
        generation.setLeaseExpiresAt(leaseExpiresAt);
        generation.setAttemptCount(generation.getAttemptCount() + 1);
        eventRepository.save(eventFactory.create(
                generation, GenerationEventType.STATUS, payloadCodec.status(GenerationStatus.RUNNING),
                now, eventExpiresAt));
        generationRepository.flush();

        var history = new ArrayList<>(messageRepository.findByConversationIdOrderBySequenceNoDesc(
                generation.getConversationId(), PageRequest.of(0, MAX_HISTORY_MESSAGES)));
        Collections.reverse(history);
        return Optional.of(new GenerationExecutionContext(
                mapper.toDomain(generation),
                history.stream().map(mapper::toDomain).toList()
        ));
    }

    @Override
    @Transactional
    public DeltaAppendResult appendDelta(
            UUID generationId,
            UUID leaseToken,
            String delta,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        GenerationTransitionTime.requireEventWindow(now, leaseExpiresAt);
        GenerationTransitionTime.requireEventWindow(now, eventExpiresAt);
        String payload = payloadCodec.delta(delta);
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        if (generation == null || !leaseToken.equals(generation.getLeaseToken())) {
            return DeltaAppendResult.STALE;
        }
        if (generation.getStatus() == GenerationStatus.CANCEL_REQUESTED) {
            return DeltaAppendResult.CANCEL_REQUESTED;
        }
        if (generation.getStatus() != GenerationStatus.RUNNING) {
            return DeltaAppendResult.STALE;
        }

        if (generation.getFirstTokenAt() == null) {
            generation.setFirstTokenAt(now);
            generation.setFirstTokenLatencyMs(
                    GenerationTransitionTime.elapsedMillis(generation.getStartedAt(), now));
        }
        generation.setUpdatedAt(now);
        generation.setLeaseExpiresAt(leaseExpiresAt);
        eventRepository.save(eventFactory.create(
                generation, GenerationEventType.DELTA, payload, now, eventExpiresAt));
        generationRepository.flush();
        return DeltaAppendResult.APPENDED;
    }

    @Override
    public Optional<AssistantGeneration> complete(
            UUID generationId,
            UUID leaseToken,
            GenerationCompletion completion
    ) {
        return terminalStore.complete(generationId, leaseToken, completion);
    }

    @Override
    public Optional<AssistantGeneration> fail(
            UUID generationId,
            UUID leaseToken,
            String errorCode,
            Instant failedAt,
            Instant eventExpiresAt
    ) {
        return terminalStore.fail(generationId, leaseToken, errorCode, failedAt, eventExpiresAt);
    }

    @Override
    public GenerationCancelResult requestCancellation(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            Instant requestedAt,
            Instant eventExpiresAt
    ) {
        return cancellationStore.request(
                generationId, conversationId, ownerUserId, requestedAt, eventExpiresAt);
    }

    @Override
    public Optional<AssistantGeneration> finishCancellation(
            UUID generationId,
            UUID leaseToken,
            Instant cancelledAt,
            Instant eventExpiresAt
    ) {
        return cancellationStore.finish(generationId, leaseToken, cancelledAt, eventExpiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findQueuedGenerationIds(int limit) {
        if (limit < 1 || limit > MAX_QUEUE_BATCH) {
            throw new IllegalArgumentException("queue batch limit must be between 1 and 1000");
        }
        return generationRepository.findIdsByStatus(GenerationStatus.QUEUED, PageRequest.of(0, limit));
    }
}
