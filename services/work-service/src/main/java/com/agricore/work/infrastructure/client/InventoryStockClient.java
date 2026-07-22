package com.agricore.work.infrastructure.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface InventoryStockClient {

    StockOutResult stockOut(UUID farmId, UUID inventoryItemId, BigDecimal quantity, String referenceId);

    record StockOutResult(UUID inventoryItemId, String unit) {
    }
}
