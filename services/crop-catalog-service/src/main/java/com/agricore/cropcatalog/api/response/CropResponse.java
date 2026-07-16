package com.agricore.cropcatalog.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CropResponse(
        UUID id,
        String code,
        String name,
        String scientificName,
        String category,
        Integer growthDaysMin,
        Integer growthDaysMax,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        BigDecimal humidityMinPct,
        BigDecimal humidityMaxPct,
        BigDecimal phMin,
        BigDecimal phMax,
        BigDecimal expectedYieldPerHa,
        String yieldUnit,
        String description,
        Instant createdAt
) {
}
