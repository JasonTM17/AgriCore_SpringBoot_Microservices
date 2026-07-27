package com.agricore.inventory.api.response;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(UUID id, UUID farmId, String code, String name, Instant createdAt) {
}
