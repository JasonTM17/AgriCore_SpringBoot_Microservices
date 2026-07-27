package com.agricore.harvest.api.response;

import java.time.Instant;
import java.util.UUID;

public record HarvestCompletionEventStatusResponse(
        UUID harvestId,
        UUID eventId,
        Producer producer,
        State state,
        Instant createdAt,
        Instant publishedAt,
        int publishAttempts
) {
    public enum Producer {
        HARVEST
    }

    public enum State {
        UNAVAILABLE,
        ENQUEUED,
        RETRYING,
        PUBLISHED
    }
}
