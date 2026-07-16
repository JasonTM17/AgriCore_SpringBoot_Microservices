package com.agricore.identity.api.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty Set<String> roles
) {
}
