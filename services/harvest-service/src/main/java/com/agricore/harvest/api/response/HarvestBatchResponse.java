package com.agricore.harvest.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HarvestBatchResponse(
        UUID id,
        String code,
        UUID cropCycleId,
        UUID plotId,
        UUID warehouseId,
        String productCode,
        BigDecimal grossWeightKg,
        BigDecimal netWeightKg,
        String qualityGrade,
        String status,
        Instant harvestedAt,
        String notes,
        String lastOutboxEventId,
        Instant createdAt,
        long version
) {
}
