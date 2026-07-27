package com.agricore.harvest.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
final class HarvestCompletionEventResolver {

    private static final String AGGREGATE_TYPE = "HarvestBatch";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final HarvestCompletionEventValidator eventValidator;

    HarvestCompletionEventResolver(
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            HarvestCompletionEventValidator eventValidator
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.eventValidator = eventValidator;
    }

    OutboxEventEntity find(HarvestBatchEntity harvest, UUID eventId) {
        return resolve(
                harvest,
                eventId,
                outboxRepository.findById(eventId),
                () -> outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        AGGREGATE_TYPE,
                        harvest.getId().toString(),
                        EventTypes.HARVEST_COMPLETED
                )
        );
    }

    OutboxEventEntity findForUpdate(HarvestBatchEntity harvest, UUID eventId) {
        return resolve(
                harvest,
                eventId,
                outboxRepository.findByIdForUpdate(eventId),
                () -> outboxRepository.findByAggregateTypeAndAggregateIdAndEventTypeForUpdate(
                        AGGREGATE_TYPE,
                        harvest.getId().toString(),
                        EventTypes.HARVEST_COMPLETED
                )
        );
    }

    private OutboxEventEntity resolve(
            HarvestBatchEntity harvest,
            UUID eventId,
            Optional<OutboxEventEntity> directCandidate,
            Supplier<Optional<OutboxEventEntity>> transitionalCandidate
    ) {
        Optional<OutboxEventEntity> directEvent = directCandidate
                .filter(event -> belongsToHarvest(event, harvest.getId()));
        if (directEvent.isPresent()) {
            OutboxEventEntity event = directEvent.get();
            requirePayloadEventId(event, eventId);
            eventValidator.requireValid(event, harvest, eventId);
            return event;
        }

        OutboxEventEntity transitionalEvent = transitionalCandidate.get()
                .filter(event -> payloadHasEventId(event, eventId))
                .orElseThrow(() -> new HarvestException(
                        "OUTBOX_EVENT_NOT_FOUND",
                        "Harvest completion event record is missing",
                        409
                ));
        eventValidator.requireValid(transitionalEvent, harvest, eventId);
        return transitionalEvent;
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
}
