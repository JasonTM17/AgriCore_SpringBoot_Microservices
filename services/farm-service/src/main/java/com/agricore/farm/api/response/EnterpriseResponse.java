package com.agricore.farm.api.response;

import java.time.Instant;
import java.util.UUID;

public record EnterpriseResponse(
        UUID id,
        String code,
        String name,
        String legalName,
        String taxCode,
        String address,
        String province,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        long version
) {
}
