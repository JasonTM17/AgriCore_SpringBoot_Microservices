package com.agricore.traceability.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTraceabilityRequest(
        @NotBlank String eventId,
        @NotNull UUID harvestBatchId,
        UUID cropCycleId,
        UUID plotId,
        String farmName,
        String plotCode,
        @NotBlank String productName,
        String varietyName,
        LocalDate plantingDate,
        @NotNull LocalDate harvestDate,
        String qualityGrade,
        BigDecimal netWeightKg,
        String careSummary
) {
}
