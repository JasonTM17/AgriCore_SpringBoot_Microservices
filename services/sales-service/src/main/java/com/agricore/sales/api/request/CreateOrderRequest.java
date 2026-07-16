package com.agricore.sales.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank String orderNumber,
        @NotNull UUID customerId,
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity
) {
}
