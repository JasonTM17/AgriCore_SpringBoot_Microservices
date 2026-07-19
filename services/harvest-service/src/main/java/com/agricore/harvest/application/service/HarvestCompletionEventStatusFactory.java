package com.agricore.harvest.application.service;

import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;

import java.util.UUID;

final class HarvestCompletionEventStatusFactory {

    private HarvestCompletionEventStatusFactory() {
    }

    static HarvestCompletionEventStatusResponse available(
            UUID harvestId,
            UUID eventId,
            OutboxEventEntity event
    ) {
        return new HarvestCompletionEventStatusResponse(
                harvestId,
                eventId,
                HarvestCompletionEventStatusResponse.Producer.HARVEST,
                stateOf(event),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getPublishAttempts()
        );
    }

    static HarvestCompletionEventStatusResponse unavailable(UUID harvestId) {
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

    private static HarvestCompletionEventStatusResponse.State stateOf(OutboxEventEntity event) {
        if (event.getPublishedAt() != null) {
            return HarvestCompletionEventStatusResponse.State.PUBLISHED;
        }
        return event.getPublishAttempts() > 0
                ? HarvestCompletionEventStatusResponse.State.RETRYING
                : HarvestCompletionEventStatusResponse.State.ENQUEUED;
    }
}
