package com.agricore.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateItemRequest(
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String itemType,
        @NotBlank @Size(max = 16) String unit
) {
}
