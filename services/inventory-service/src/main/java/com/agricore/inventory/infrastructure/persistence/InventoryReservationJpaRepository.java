package com.agricore.inventory.infrastructure.persistence;

import com.agricore.inventory.infrastructure.persistence.entity.InventoryReservationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservationEntity, UUID> {

    Optional<InventoryReservationEntity> findByReferenceTypeAndReferenceId(
            String referenceType,
            String referenceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reservation
            FROM InventoryReservationEntity reservation
            WHERE reservation.id = :id
            """)
    Optional<InventoryReservationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reservation
            FROM InventoryReservationEntity reservation
            WHERE reservation.referenceType = :referenceType
              AND reservation.referenceId = :referenceId
            """)
    Optional<InventoryReservationEntity> findByReferenceForUpdate(
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId
    );
}
