package com.agricore.farm.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateFarmRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 120) String province,
        @DecimalMin("0.0") BigDecimal totalAreaHa,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 32) String status
) {
}
