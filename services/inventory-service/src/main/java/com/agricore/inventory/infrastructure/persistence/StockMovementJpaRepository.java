package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, UUID> {
}
