package com.agricore.farm.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SoilProfileResponse(
        UUID id,
        UUID farmId,
        UUID plotId,
        String sampleCode,
        LocalDate sampledAt,
        BigDecimal sampleDepthCm,
        String texture,
        BigDecimal ph,
        BigDecimal organicMatterPercent,
        BigDecimal nitrogenMgKg,
        BigDecimal phosphorusMgKg,
        BigDecimal potassiumMgKg,
        BigDecimal moisturePercent,
        String notes,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        long version
) {
}
