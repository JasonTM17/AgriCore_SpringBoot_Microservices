package com.agricore.inventory.api.response;

import java.util.UUID;

public record InternalWarehouseScopeResponse(
        UUID warehouseId,
        UUID farmId
) {
}
