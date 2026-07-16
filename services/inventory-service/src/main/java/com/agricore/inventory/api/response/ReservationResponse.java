package com.agricore.inventory.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID inventoryItemId,
        BigDecimal quantity,
        String status,
        String referenceType,
        String referenceId
) {
}
