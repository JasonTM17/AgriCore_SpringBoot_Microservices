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
        @NotNull UUID customerId,
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity,
        @DecimalMin("0.0000") @Digits(integer = 14, fraction = 4) BigDecimal unitPrice,
        @Pattern(regexp = "[A-Za-z]{3}") String currencyCode
) {
    /**
     * Keeps the source-compatible v1 constructor while allowing an optional
     * price snapshot for newer callers.
     */
    public CreateOrderRequest(
            String orderNumber,
            UUID customerId,
            UUID inventoryItemId,
            BigDecimal quantity
    ) {
        this(orderNumber, customerId, inventoryItemId, quantity, null, null);
    }
}
