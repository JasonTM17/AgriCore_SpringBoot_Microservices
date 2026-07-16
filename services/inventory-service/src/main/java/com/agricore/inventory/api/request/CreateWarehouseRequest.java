package com.agricore.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name
) {
}
