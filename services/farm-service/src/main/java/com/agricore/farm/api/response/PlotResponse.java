package com.agricore.farm.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlotResponse(
        UUID id,
        UUID farmId,
        UUID areaId,
        String code,
        String name,
        BigDecimal areaInHectares,
        String soilType,
        String status,
        Double latitude,
        Double longitude,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
