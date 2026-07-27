package com.agricore.iot.api.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IngestReadingRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String deviceCode,
        @NotBlank @Size(max = 64) String metricType,
        @NotNull @Digits(integer = 10, fraction = 4) BigDecimal metricValue,
        @NotBlank @Size(max = 16) String unit,
        Instant recordedAt,
        UUID readingId
) {
    public IngestReadingRequest(
            String deviceCode,
            String metricType,
            BigDecimal metricValue,
            String unit,
            Instant recordedAt
    ) {
        this(deviceCode, metricType, metricValue, unit, recordedAt, null);
    }
}
