package com.agricore.inventory.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal/sync adapter for HarvestCompleted.v1 payload (also used by tests simulating Kafka redelivery).
 */
public record HarvestCompletedCommand(
        @NotBlank String eventId,
        @NotNull UUID harvestBatchId,
        @NotNull UUID warehouseId,
        @NotBlank String productCode,
        @NotNull @DecimalMin("0.001") BigDecimal netWeightKg,
        String qualityGrade
) {
}
