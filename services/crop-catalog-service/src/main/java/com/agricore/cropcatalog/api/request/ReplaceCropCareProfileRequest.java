package com.agricore.cropcatalog.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceCropCareProfileRequest(
        @PositiveOrZero long version,
        @Valid @NotNull GrowthRequirementInput growthRequirement,
        @NotNull @Size(max = 100) List<@Valid @NotNull CommonDiseaseInput> commonDiseases,
        @NotNull @Size(max = 200) List<@Valid @NotNull CareRecommendationInput> recommendations
) {
}
