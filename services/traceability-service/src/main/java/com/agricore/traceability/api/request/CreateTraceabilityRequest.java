package com.agricore.traceability.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTraceabilityRequest(
        @NotNull UUID eventId,
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
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal netWeightKg,
        @Size(max = 1000) String careSummary,
        @Size(max = 64) String productCode,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal grossWeightKg
) {
    public CreateTraceabilityRequest(
            UUID eventId,
            UUID harvestBatchId,
            UUID cropCycleId,
            UUID plotId,
            String farmName,
            String plotCode,
            String productName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate harvestDate,
            String qualityGrade,
            BigDecimal netWeightKg,
            String careSummary
    ) {
        this(
                eventId,
                harvestBatchId,
                cropCycleId,
                plotId,
                farmName,
                plotCode,
                productName,
                varietyName,
                plantingDate,
                harvestDate,
                qualityGrade,
                netWeightKg,
                careSummary,
                null,
                null
        );
    }
}
