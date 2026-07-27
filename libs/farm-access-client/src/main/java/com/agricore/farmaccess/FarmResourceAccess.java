package com.agricore.farmaccess;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

public record FarmResourceAccess(
        @JsonProperty(value = "farmId", required = true) UUID farmId,
        @JsonProperty(value = "plotId", required = true) UUID plotId
) {

    public FarmResourceAccess {
        Objects.requireNonNull(farmId, "farmId");
    }
}
