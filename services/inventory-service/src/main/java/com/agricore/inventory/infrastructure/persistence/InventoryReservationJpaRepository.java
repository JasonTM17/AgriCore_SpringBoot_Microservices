package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.InventoryReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservationEntity, UUID> {

    Optional<InventoryReservationEntity> findByReferenceTypeAndReferenceId(
            String referenceType,
            String referenceId
    );
}
