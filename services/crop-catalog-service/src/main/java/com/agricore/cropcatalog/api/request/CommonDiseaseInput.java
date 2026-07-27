package com.agricore.cropcatalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CommonDiseaseInput(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 4000) String symptoms,
        @NotBlank @Size(max = 4000) String prevention,
        @NotBlank @Size(max = 4000) String treatment
) {
}
