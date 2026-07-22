package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.StockMovementEntity;
import com.agricore.inventory.domain.model.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, UUID> {
    Optional<StockMovementEntity> findFirstByInventoryItemIdAndMovementTypeAndReferenceTypeAndReferenceId(
            UUID inventoryItemId,
            MovementType movementType,
            String referenceType,
            String referenceId
    );
}
