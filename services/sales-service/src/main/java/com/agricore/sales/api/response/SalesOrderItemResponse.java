package com.agricore.sales.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemResponse(
        int lineNumber,
        UUID inventoryItemId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currencyCode
) {
}
