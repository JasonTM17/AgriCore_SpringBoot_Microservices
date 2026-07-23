package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateIrrigationZoneRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{0,63}") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull
        @Pattern(regexp = "(?i)DRIP|SPRINKLER|MICRO_SPRINKLER|CENTER_PIVOT|FLOOD|MANUAL")
        String method,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999.99")
        @Digits(integer = 6, fraction = 2) BigDecimal flowRateLitersPerMinute,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2) BigDecimal targetMoisturePercent,
        @Size(max = 1000) String notes
) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown irrigation-zone field: " + field);
    }
}
