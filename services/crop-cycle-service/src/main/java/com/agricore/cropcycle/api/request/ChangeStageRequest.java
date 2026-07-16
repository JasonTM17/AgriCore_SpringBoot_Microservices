package com.agricore.cropcycle.api.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeStageRequest(
        @NotBlank String stage,
        String notes
) {
}
