package com.agricore.farm.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateFarmAreaRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(min = 1, max = 200) @Pattern(regexp = ".*\\S.*") String name,
        @DecimalMin("0.0001") @Digits(integer = 10, fraction = 4) BigDecimal areaInHectares,
        @Size(max = 500) String description,
        @Pattern(regexp = "(?i)ACTIVE|INACTIVE|MAINTENANCE") String status
) {
}
