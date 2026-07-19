package com.agricore.cropcycle.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeStageRequest(
        @NotBlank String stage,
        @Size(max = 2000) String notes
) {
}
