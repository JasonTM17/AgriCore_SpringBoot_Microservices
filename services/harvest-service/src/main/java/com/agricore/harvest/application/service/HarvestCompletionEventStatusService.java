package com.agricore.harvest.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class HarvestCompletionEventStatusService {

    private static final String AGGREGATE_TYPE = "HarvestBatch";

    private final HarvestBatchJpaRepository harvestRepository;
    private final OutboxJpaRepository outboxRepository;
    private final HarvestAccessGuard accessGuard;
    private final ObjectMapper objectMapper;

    public HarvestCompletionEventStatusService(
            HarvestBatchJpaRepository harvestRepository,
            OutboxJpaRepository outboxRepository,
            HarvestAccessGuard accessGuard,
            ObjectMapper objectMapper
    ) {
        this.harvestRepository = harvestRepository;
        this.outboxRepository = outboxRepository;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HarvestCompletionEventStatusResponse getStatus(UUID harvestId) {
        HarvestBatchEntity harvest = harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestException(
                        "HARVEST_NOT_FOUND",
                        "Harvest batch not found",
                        404
                ));
        accessGuard.requireExistingHarvestPlot(harvest.getPlotId());

        UUID eventId = harvest.getLastOutboxEventId();
        if (eventId == null) {
            return unavailable(harvestId);
        }

        OutboxEventEntity outboxEvent = findCompletionEvent(harvestId, eventId);
        return new HarvestCompletionEventStatusResponse(
                harvestId,
                eventId,
                HarvestCompletionEventStatusResponse.Producer.HARVEST,
                stateOf(outboxEvent),
                outboxEvent.getCreatedAt(),
                outboxEvent.getPublishedAt(),
                outboxEvent.getPublishAttempts()
        );
    }

    private OutboxEventEntity findCompletionEvent(UUID harvestId, UUID eventId) {
        Optional<OutboxEventEntity> directEvent = outboxRepository.findById(eventId)
                .filter(event -> belongsToHarvest(event, harvestId));
        if (directEvent.isEmpty()) {
            return findTransitionalEvent(harvestId, eventId);
        }

        OutboxEventEntity event = directEvent.get();
        requirePayloadEventId(event, eventId);
        return event;
    }

    private OutboxEventEntity findTransitionalEvent(UUID harvestId, UUID eventId) {
        return outboxRepository
                .findByAggregateTypeAndAggregateIdAndEventType(
                        AGGREGATE_TYPE,
                        harvestId.toString(),
                        EventTypes.HARVEST_COMPLETED
                )
                .filter(event -> payloadHasEventId(event, eventId))
                .orElseThrow(() -> new HarvestException(
                        "OUTBOX_EVENT_NOT_FOUND",
                        "Harvest completion event record is missing",
                        409
                ));
    }

    private boolean payloadHasEventId(OutboxEventEntity event, UUID eventId) {
        try {
            String payloadEventId = objectMapper.readTree(event.getPayload()).path("eventId").asText();
            return eventId.equals(UUID.fromString(payloadEventId));
        } catch (Exception ex) {
            throw new HarvestException(
                    "OUTBOX_EVENT_INVALID",
                    "Harvest completion event payload is invalid",
                    409
            );
        }
    }

    private void requirePayloadEventId(OutboxEventEntity event, UUID eventId) {
        if (!payloadHasEventId(event, eventId)) {
            throw new HarvestException(
                    "OUTBOX_EVENT_ID_MISMATCH",
                    "Harvest completion event identity does not match its payload",
                    409
            );
        }
    }

    private static boolean belongsToHarvest(OutboxEventEntity event, UUID harvestId) {
        return AGGREGATE_TYPE.equals(event.getAggregateType())
                && harvestId.toString().equals(event.getAggregateId())
                && EventTypes.HARVEST_COMPLETED.equals(event.getEventType());
    }

    private static HarvestCompletionEventStatusResponse.State stateOf(OutboxEventEntity event) {
        if (event.getPublishedAt() != null) {
            return HarvestCompletionEventStatusResponse.State.PUBLISHED;
        }
        return event.getPublishAttempts() > 0
                ? HarvestCompletionEventStatusResponse.State.RETRYING
                : HarvestCompletionEventStatusResponse.State.ENQUEUED;
    }

    private static HarvestCompletionEventStatusResponse unavailable(UUID harvestId) {
        return new HarvestCompletionEventStatusResponse(
                harvestId,
                null,
                HarvestCompletionEventStatusResponse.Producer.HARVEST,
                HarvestCompletionEventStatusResponse.State.UNAVAILABLE,
                null,
                null,
                0
        );
    }
}
