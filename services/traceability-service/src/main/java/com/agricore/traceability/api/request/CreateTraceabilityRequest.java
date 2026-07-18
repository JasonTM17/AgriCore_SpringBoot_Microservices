package com.agricore.traceability.api.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTraceabilityRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotNull UUID harvestBatchId,
        UUID cropCycleId,
        UUID plotId,
        @Size(max = 200) String farmName,
        @Size(max = 64) String plotCode,
        @NotBlank @Size(max = 200) String productName,
        @Size(max = 200) String varietyName,
        LocalDate plantingDate,
        @NotNull LocalDate harvestDate,
        @Size(max = 32) String qualityGrade,
        @Digits(integer = 11, fraction = 3) BigDecimal netWeightKg,
        String careSummary
) {
}
