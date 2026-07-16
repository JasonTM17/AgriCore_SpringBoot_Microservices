package com.agricore.inventory.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotBlank String referenceType,
        @NotBlank String referenceId
) {
}
