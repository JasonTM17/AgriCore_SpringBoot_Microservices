package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderSagaJpaRepository extends JpaRepository<OrderSagaEntity, UUID> {
    Optional<OrderSagaEntity> findBySalesOrderId(UUID salesOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT saga FROM OrderSagaEntity saga WHERE saga.salesOrderId = :salesOrderId")
    Optional<OrderSagaEntity> findBySalesOrderIdForUpdate(UUID salesOrderId);
}
