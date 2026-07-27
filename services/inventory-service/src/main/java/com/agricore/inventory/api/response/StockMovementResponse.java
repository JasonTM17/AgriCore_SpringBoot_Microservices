package com.agricore.inventory.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID itemId,
        UUID batchId,
        String type,
        BigDecimal quantity,
        String referenceType,
        String referenceId,
        String note,
        Instant createdAt
) {
}
