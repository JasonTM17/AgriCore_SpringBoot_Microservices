package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.InventoryBatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchJpaRepository extends JpaRepository<InventoryBatchEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from InventoryBatchEntity batch
            where batch.inventoryItemId = :inventoryItemId
            order by
                case when batch.expiresAt is null then 1 else 0 end,
                batch.expiresAt asc,
                batch.receivedAt asc,
                batch.id asc
            """)
    List<InventoryBatchEntity> findAllByInventoryItemIdForUpdate(
            @Param("inventoryItemId") UUID inventoryItemId
    );

    List<InventoryBatchEntity> findAllByInventoryItemIdOrderByReceivedAtAscIdAsc(UUID inventoryItemId);

    Optional<InventoryBatchEntity> findByInventoryItemIdAndLotCode(UUID inventoryItemId, String lotCode);
}
