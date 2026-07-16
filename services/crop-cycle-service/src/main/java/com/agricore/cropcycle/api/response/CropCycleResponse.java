package com.agricore.cropcycle.api.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CropCycleResponse(
        UUID id,
        String code,
        UUID farmId,
        UUID plotId,
        UUID cropId,
        UUID cropVarietyId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        String stage,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
