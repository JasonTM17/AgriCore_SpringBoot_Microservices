package com.agricore.cropcycle.api.response;

import com.agricore.cropcycle.domain.model.ObservationCategory;
import com.agricore.cropcycle.domain.model.ObservationSeverity;

import java.time.Instant;
import java.util.UUID;

public record CropCycleObservationResponse(
        UUID id,
        UUID cropCycleId,
        ObservationCategory category,
        ObservationSeverity severity,
        String title,
        String details,
        Instant observedAt,
        String recordedBy,
        Instant createdAt
) {
}
