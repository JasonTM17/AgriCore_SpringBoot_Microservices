package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SalesOrderJpaRepository extends JpaRepository<SalesOrderEntity, UUID> {
    boolean existsByOrderNumberIgnoreCase(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT salesOrder FROM SalesOrderEntity salesOrder WHERE salesOrder.id = :id")
    Optional<SalesOrderEntity> findByIdForUpdate(UUID id);
}
