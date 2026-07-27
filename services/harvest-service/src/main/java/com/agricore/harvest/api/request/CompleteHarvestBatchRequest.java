package com.agricore.harvest.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CompleteHarvestBatchRequest(
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal grossWeightKg,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal netWeightKg,
        @NotBlank @Size(max = 32) String qualityGrade,
        @Size(max = 2000) String notes,
        @Size(max = 200) String farmName,
        @Size(max = 64) String plotCode,
        @Size(max = 200) String productName,
        @Size(max = 1000) String careSummary
) {
}
