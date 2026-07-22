package com.agricore.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record UpdateRolePermissionsRequest(
        @NotNull
        @Size(max = 100)
        Set<@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String> permissionCodes
) {
    public UpdateRolePermissionsRequest {
        if (permissionCodes != null) {
            permissionCodes = Collections.unmodifiableSet(new HashSet<>(permissionCodes));
        }
    }
}
