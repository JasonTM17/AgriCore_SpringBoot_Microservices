package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemEntity, UUID> {
    Optional<InventoryItemEntity> findByWarehouseIdAndSkuIgnoreCase(UUID warehouseId, String sku);
}
