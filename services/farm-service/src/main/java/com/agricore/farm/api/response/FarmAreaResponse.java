package com.agricore.farm.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FarmAreaResponse(
        UUID id,
        UUID farmId,
        String code,
        String name,
        BigDecimal areaInHectares,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        long version
) {
}
