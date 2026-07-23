package com.agricore.cropcatalog.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record GrowthRequirementResponse(
        int irrigationIntervalDaysMin,
        int irrigationIntervalDaysMax,
        int fertilizationIntervalDaysMin,
        int fertilizationIntervalDaysMax,
        BigDecimal waterRequirementMmPerWeek,
        String notes,
        Instant updatedAt
) {
}
