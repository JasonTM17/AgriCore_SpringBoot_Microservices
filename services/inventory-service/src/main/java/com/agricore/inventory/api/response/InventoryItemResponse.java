package com.agricore.inventory.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        UUID warehouseId,
        String sku,
        String name,
        String itemType,
        String unit,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        long version
) {
}
