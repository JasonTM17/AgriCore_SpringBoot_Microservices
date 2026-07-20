package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.DeltaAppendResult;
import com.agricore.assistant.application.model.GenerationCancelResult;
import com.agricore.assistant.application.model.GenerationCompletion;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.GenerationLeaseStatus;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GenerationExecutionPersistenceAdapter implements GenerationExecutionRepository {

    private static final int MAX_QUEUE_BATCH = 1_000;

    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationLeaseTransitionStore leaseStore;
    private final GenerationTerminalTransitionStore terminalStore;
    private final GenerationCancellationTransitionStore cancellationStore;

    public GenerationExecutionPersistenceAdapter(
            ChatGenerationJpaRepository generationRepository,
            GenerationLeaseTransitionStore leaseStore,
            GenerationTerminalTransitionStore terminalStore,
            GenerationCancellationTransitionStore cancellationStore
    ) {
        this.generationRepository = generationRepository;
        this.leaseStore = leaseStore;
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
        return leaseStore.claim(generationId, leaseToken, now, leaseExpiresAt, eventExpiresAt);
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
        return leaseStore.appendDelta(
                generationId, leaseToken, delta, now, leaseExpiresAt, eventExpiresAt);
    }

    @Override
    public GenerationLeaseStatus renewLease(
            UUID generationId,
            UUID leaseToken,
            Instant now,
            Instant leaseExpiresAt
    ) {
        return leaseStore.renewLease(generationId, leaseToken, now, leaseExpiresAt);
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
