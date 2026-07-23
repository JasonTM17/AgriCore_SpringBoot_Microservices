package com.agricore.cropcatalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CareRecommendationInput(
        @NotNull CareRecommendationCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String growthStage,
        @PositiveOrZero int sortOrder
) {
}
