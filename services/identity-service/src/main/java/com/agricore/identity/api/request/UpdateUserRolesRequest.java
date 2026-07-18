package com.agricore.identity.api.request;

import com.agricore.identity.domain.model.RoleCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty @Size(max = 7) Set<@NotNull RoleCode> roles
) {
}
