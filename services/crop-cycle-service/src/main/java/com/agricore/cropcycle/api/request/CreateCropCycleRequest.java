package com.agricore.cropcycle.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCropCycleRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID farmId,
        @NotNull UUID plotId,
        @NotNull UUID cropId,
        UUID cropVarietyId,
        @NotNull LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        @Size(max = 2000) String notes
) {
}
