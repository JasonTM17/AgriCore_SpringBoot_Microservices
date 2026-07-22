package com.agricore.cropcatalog.api.response;

import java.time.Instant;
import java.util.UUID;

public record CropVarietyResponse(
        UUID id,
        UUID cropId,
        String code,
        String name,
        String origin,
        String notes,
        Instant createdAt
) {
}
