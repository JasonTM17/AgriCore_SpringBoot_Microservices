package com.agricore.inventory.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
        @NotBlank @Size(max = 64) String referenceType,
        @NotBlank @Size(max = 100) String referenceId
) {
}
