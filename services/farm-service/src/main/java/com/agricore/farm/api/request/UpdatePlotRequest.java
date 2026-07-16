package com.agricore.farm.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePlotRequest(
        @Size(max = 200) String name,
        @DecimalMin("0.0001") BigDecimal areaInHectares,
        @Size(max = 100) String soilType,
        @Size(max = 32) String status,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}
