package com.agricore.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWarehouseRequest(
        @NotNull UUID farmId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name
) {
}
