package com.agricore.farm.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String code,
        String name,
        String address,
        String province,
        BigDecimal totalAreaHa,
        Double latitude,
        Double longitude,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
