package com.agricore.traceability.api.response;

import java.time.Instant;
import java.util.UUID;

public record TraceabilityHarvestProjectionAcknowledgementResponse(
        UUID eventId,
        String projection,
        State state,
        Instant acknowledgedAt
) {
    public enum State {
        ACKNOWLEDGED,
        NOT_ACKNOWLEDGED
    }
}
