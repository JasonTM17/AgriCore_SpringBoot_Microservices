package com.agricore.farm.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IrrigationZoneResponse(
        UUID id,
        UUID farmId,
        UUID plotId,
        String code,
        String name,
        String method,
        BigDecimal flowRateLitersPerMinute,
        BigDecimal targetMoisturePercent,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        long version
) {
}
