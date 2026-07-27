package com.agricore.harvest.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StartHarvestRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID cropCycleId,
        @NotNull UUID plotId,
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 64) String productCode,
        @Size(max = 2000) String notes
) {
}
