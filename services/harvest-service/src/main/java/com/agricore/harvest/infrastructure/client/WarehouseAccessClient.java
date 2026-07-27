package com.agricore.harvest.infrastructure.client;

import java.util.UUID;

public interface WarehouseAccessClient {

    void requireWarehouse(UUID warehouseId, UUID farmId);
}
