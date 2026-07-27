package com.agricore.farm.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantFarmMembershipRequest(
        @NotNull UUID subject
) {
}
