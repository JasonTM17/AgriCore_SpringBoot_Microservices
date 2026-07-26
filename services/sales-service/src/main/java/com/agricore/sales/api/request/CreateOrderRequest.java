package com.agricore.sales.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank String orderNumber,
        @NotNull UUID farmId,
        @NotNull UUID customerId,
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
        @DecimalMin("0.0000") @Digits(integer = 14, fraction = 4) BigDecimal unitPrice,
        @Pattern(regexp = "[A-Za-z]{3}") String currencyCode
) {
    public CreateOrderRequest(
            String orderNumber,
            UUID farmId,
            UUID customerId,
            UUID inventoryItemId,
            BigDecimal quantity
    ) {
        this(orderNumber, farmId, customerId, inventoryItemId, quantity, null, null);
    }
}
