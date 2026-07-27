package com.agricore.work.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaterialUsageResponse(
        UUID id,
        UUID inventoryItemId,
        BigDecimal quantity,
        String unit,
        String status,
        String inventoryReferenceId,
        String lastError,
        Instant consumedAt
) {
}
