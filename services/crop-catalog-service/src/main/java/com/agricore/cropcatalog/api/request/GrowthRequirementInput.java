package com.agricore.cropcatalog.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GrowthRequirementInput(
        @Min(1) @Max(3650) int irrigationIntervalDaysMin,
        @Min(1) @Max(3650) int irrigationIntervalDaysMax,
        @Min(1) @Max(3650) int fertilizationIntervalDaysMin,
        @Min(1) @Max(3650) int fertilizationIntervalDaysMax,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") BigDecimal waterRequirementMmPerWeek,
        @Size(max = 4000) String notes
) {
}
