package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<WarehouseEntity> findByCodeIgnoreCase(String code);
}
