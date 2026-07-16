package com.agricore.iot.api.response;

import java.util.UUID;

public record IngestResultResponse(
        UUID readingId,
        boolean alertRaised,
        UUID alertId,
        String alertStatus,
        String message
) {
}
