package com.agricore.iot.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record IngestReadingRequest(
        @NotBlank @Size(max = 64) String deviceCode,
        @NotBlank String metricType,
        @NotNull BigDecimal metricValue,
        @NotBlank @Size(max = 16) String unit,
        Instant recordedAt
) {
}
