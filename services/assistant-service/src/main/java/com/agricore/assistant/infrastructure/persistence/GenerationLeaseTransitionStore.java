package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.GenerationLeaseStatus;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class GenerationLeaseTransitionStore {

    private static final int MAX_HISTORY_MESSAGES = 100;

    private final ChatGenerationJpaRepository generationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;
    private final GenerationEventFactory eventFactory;
    private final GenerationEventPayloadCodec payloadCodec;

    public GenerationLeaseTransitionStore(
            ChatGenerationJpaRepository generationRepository,
            ConversationMessageJpaRepository messageRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper,
            GenerationEventFactory eventFactory,
            GenerationEventPayloadCodec payloadCodec
    ) {
        this.generationRepository = generationRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.eventFactory = eventFactory;
        this.payloadCodec = payloadCodec;
    }

    @Transactional
    public Optional<GenerationExecutionContext> claim(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    ) {
        requireLeaseIdentity(generationId, leaseToken);
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

    @Transactional
    public DeltaAppendResult appendDelta(
            UUID generationId,
            UUID leaseToken,
            String delta,
            Instant now,
            Instant leaseExpiresAt,
            Instant eventExpiresAt
    ) {
        requireLeaseIdentity(generationId, leaseToken);
        GenerationTransitionTime.requireEventWindow(now, leaseExpiresAt);
        GenerationTransitionTime.requireEventWindow(now, eventExpiresAt);
        String payload = payloadCodec.delta(delta);
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        GenerationLeaseStatus leaseStatus = status(generation, leaseToken);
        if (leaseStatus == GenerationLeaseStatus.CANCEL_REQUESTED) {
            return DeltaAppendResult.CANCEL_REQUESTED;
        }
        if (leaseStatus == GenerationLeaseStatus.STALE) {
            return DeltaAppendResult.STALE;
        }

        if (generation.getFirstTokenAt() == null) {
            generation.setFirstTokenAt(now);
            generation.setFirstTokenLatencyMs(
                    GenerationTransitionTime.elapsedMillis(generation.getStartedAt(), now));
        }
        renew(generation, now, leaseExpiresAt);
        eventRepository.save(eventFactory.create(
                generation, GenerationEventType.DELTA, payload, now, eventExpiresAt));
        generationRepository.flush();
        return DeltaAppendResult.APPENDED;
    }

    @Transactional
    public GenerationLeaseStatus renewLease(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt
    ) {
        requireLeaseIdentity(generationId, leaseToken);
        GenerationTransitionTime.requireEventWindow(now, leaseExpiresAt);
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        GenerationLeaseStatus status = status(generation, leaseToken);
        if (status != GenerationLeaseStatus.ACTIVE) {
            return status;
        }
        renew(generation, now, leaseExpiresAt);
        generationRepository.flush();
        return GenerationLeaseStatus.ACTIVE;
    }

    private static GenerationLeaseStatus status(ChatGenerationEntity generation, UUID leaseToken) {
        if (generation == null || !leaseToken.equals(generation.getLeaseToken())) {
            return GenerationLeaseStatus.STALE;
        }
        if (generation.getStatus() == GenerationStatus.CANCEL_REQUESTED) {
            return GenerationLeaseStatus.CANCEL_REQUESTED;
        }
        return generation.getStatus() == GenerationStatus.RUNNING
                ? GenerationLeaseStatus.ACTIVE
                : GenerationLeaseStatus.STALE;
    }

    private static void renew(ChatGenerationEntity generation, Instant now, Instant leaseExpiresAt) {
        generation.setUpdatedAt(now);
        generation.setLeaseExpiresAt(leaseExpiresAt);
    }

    private static void requireLeaseIdentity(UUID generationId, UUID leaseToken) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
    }
}
