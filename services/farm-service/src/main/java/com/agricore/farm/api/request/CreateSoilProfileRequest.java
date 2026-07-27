package com.agricore.farm.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSoilProfileRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{0,63}") String sampleCode,
        @NotNull @PastOrPresent LocalDate sampledAt,
        @NotNull @DecimalMin("0.01") @DecimalMax("999.99")
        @Digits(integer = 3, fraction = 2) BigDecimal sampleDepthCm,
        @NotNull @Pattern(regexp = "(?i)SAND|LOAMY_SAND|SANDY_LOAM|LOAM|SILT_LOAM|SILT|"
                + "SANDY_CLAY_LOAM|CLAY_LOAM|SILTY_CLAY_LOAM|SANDY_CLAY|SILTY_CLAY|CLAY")
        String texture,
        @NotNull @DecimalMin("0.00") @DecimalMax("14.00")
        @Digits(integer = 2, fraction = 2) BigDecimal ph,
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2) BigDecimal organicMatterPercent,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal nitrogenMgKg,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal phosphorusMgKg,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal potassiumMgKg,
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2) BigDecimal moisturePercent,
        @Size(max = 1000) String notes
) {
}
