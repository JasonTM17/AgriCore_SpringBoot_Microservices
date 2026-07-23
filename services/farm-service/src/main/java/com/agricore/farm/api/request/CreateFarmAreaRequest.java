package com.agricore.farm.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateFarmAreaRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 10, fraction = 4) BigDecimal areaInHectares,
        @Size(max = 500) String description
) {
}
