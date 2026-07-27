package com.agricore.work.application.service;

import com.agricore.work.api.request.MaterialUsageRequest;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MaterialUsageRequestMatcher {

    private MaterialUsageRequestMatcher() {
    }

    static void validateNoDuplicateItems(List<MaterialUsageRequest> requestedMaterials) {
        Set<UUID> inventoryItemIds = new HashSet<>();
        boolean duplicate = requestedMaterials.stream()
                .map(MaterialUsageRequest::inventoryItemId)
                .anyMatch(itemId -> !inventoryItemIds.add(itemId));
        if (duplicate) {
            throw new WorkException(
                    "DUPLICATE_MATERIAL_ITEM",
                    "Each inventory item may appear only once per task completion",
                    400
            );
        }
    }

    static void validateRetryMatches(
            List<MaterialUsageEntity> existing,
            List<MaterialUsageRequest> requested
    ) {
        Map<UUID, MaterialUsageEntity> existingByItem = new HashMap<>();
        existing.forEach(usage -> existingByItem.put(usage.getInventoryItemId(), usage));
        boolean mismatch = existing.size() != requested.size() || requested.stream().anyMatch(request -> {
            MaterialUsageEntity usage = existingByItem.get(request.inventoryItemId());
            return usage == null || usage.getQuantity().compareTo(request.quantity()) != 0;
        });
        if (mismatch) {
            throw new WorkException(
                    "MATERIAL_REQUEST_CHANGED",
                    "Retry must use the original material items and quantities",
                    409
            );
        }
    }
}
