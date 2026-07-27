package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.InventoryReservationAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationAllocationJpaRepository
        extends JpaRepository<InventoryReservationAllocationEntity, UUID> {

    List<InventoryReservationAllocationEntity> findAllByReservationIdOrderByCreatedAtAscIdAsc(
            UUID reservationId
    );
}
