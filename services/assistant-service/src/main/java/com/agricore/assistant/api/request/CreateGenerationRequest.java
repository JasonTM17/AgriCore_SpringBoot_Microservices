package com.agricore.assistant.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGenerationRequest(
        @NotBlank(message = "prompt is required")
        @Size(max = 200_000, message = "prompt is too long")
        String prompt
) {
}
