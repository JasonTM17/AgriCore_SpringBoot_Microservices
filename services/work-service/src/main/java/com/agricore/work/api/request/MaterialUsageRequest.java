package com.agricore.work.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record MaterialUsageRequest(
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 15, fraction = 3) BigDecimal quantity
) {
}
