package com.agricore.cropcycle.api.request;

import com.agricore.cropcycle.domain.model.ObservationCategory;
import com.agricore.cropcycle.domain.model.ObservationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateCropCycleObservationRequest(
        @NotNull ObservationCategory category,
        @NotNull ObservationSeverity severity,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String details,
        @NotNull @PastOrPresent Instant observedAt
) {
}
