package com.agricore.sales.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
        UUID id,
        String orderNumber,
        UUID customerId,
        String status,
        UUID inventoryItemId,
        BigDecimal quantity,
        UUID reservationId,
        UUID correlationId,
        String failureReason,
        String sagaStatus,
        String sagaStep,
        Instant createdAt,
        String currencyCode,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        List<SalesOrderItemResponse> items
) {
}
