package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class GenerationLeaseExpiryStore {

    private static final int MAX_EXPIRY_BATCH = 1_000;
    private static final String WORKER_LOST_CODE = "GENERATION_WORKER_LOST";
    private static final List<GenerationStatus> EXPIRABLE_STATUSES = List.of(
            GenerationStatus.RUNNING,
            GenerationStatus.CANCEL_REQUESTED
    );

    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationEventFactory eventFactory;
    private final GenerationEventPayloadCodec payloadCodec;

    public GenerationLeaseExpiryStore(
            ChatGenerationJpaRepository generationRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationEventFactory eventFactory,
            GenerationEventPayloadCodec payloadCodec
    ) {
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.eventFactory = eventFactory;
        this.payloadCodec = payloadCodec;
    }

    @Transactional
    public int expire(Instant now, Instant eventExpiresAt, int limit) {
        GenerationTransitionTime.requireEventWindow(now, eventExpiresAt);
        if (limit < 1 || limit > MAX_EXPIRY_BATCH) {
            throw new IllegalArgumentException("expiry batch limit must be between 1 and 1000");
        }
        List<ChatGenerationEntity> expired = generationRepository.findExpiredLeasesForUpdate(
                EXPIRABLE_STATUSES, now, PageRequest.of(0, limit));
        expired.forEach(generation -> expire(generation, now, eventExpiresAt));
        generationRepository.flush();
        return expired.size();
    }

    private void expire(
            ChatGenerationEntity generation,
            Instant now,
            Instant eventExpiresAt
    ) {
        boolean cancellationRequested = generation.getStatus() == GenerationStatus.CANCEL_REQUESTED;
        GenerationStatus terminalStatus = cancellationRequested
                ? GenerationStatus.CANCELLED
                : GenerationStatus.FAILED;
        String errorCode = cancellationRequested ? null : WORKER_LOST_CODE;

        generation.setStatus(terminalStatus);
        generation.setActiveConversationId(null);
        generation.setErrorCode(errorCode);
        generation.setLeaseToken(null);
        generation.setLeaseExpiresAt(null);
        generation.setCompletedAt(now);
        generation.setUpdatedAt(now);
        generation.setProviderLatencyMs(GenerationTransitionTime.elapsedMillisIfStarted(
                generation.getStartedAt(), now));
        generation.setTotalLatencyMs(GenerationTransitionTime.elapsedMillis(
                generation.getQueuedAt(), now));
        if (cancellationRequested) {
            generation.setCancelledAt(now);
        }

        eventRepository.save(eventFactory.create(
                generation,
                cancellationRequested ? GenerationEventType.CANCELLED : GenerationEventType.ERROR,
                cancellationRequested ? payloadCodec.cancelled() : payloadCodec.error(errorCode),
                now,
                eventExpiresAt
        ));
    }
}
