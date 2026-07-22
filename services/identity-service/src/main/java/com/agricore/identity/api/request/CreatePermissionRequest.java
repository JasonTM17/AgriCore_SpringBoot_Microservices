package com.agricore.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
