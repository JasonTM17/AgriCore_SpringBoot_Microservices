package com.agricore.harvest.api.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CompleteHarvestRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID cropCycleId,
        @NotNull UUID plotId,
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 64) String productCode,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal grossWeightKg,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal netWeightKg,
        @NotBlank @Size(max = 32) String qualityGrade,
        @Size(max = 2000) String notes,
        /** Denormalized for Traceability QR projection via HarvestCompleted.v1 */
        @Size(max = 200) String farmName,
        @Size(max = 64) String plotCode,
        @Size(max = 200) String productName,
        @Size(max = 1000) String careSummary
) {
}
