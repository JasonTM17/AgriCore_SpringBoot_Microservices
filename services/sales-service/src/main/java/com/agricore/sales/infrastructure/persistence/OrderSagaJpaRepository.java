package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderSagaJpaRepository extends JpaRepository<OrderSagaEntity, UUID> {
    Optional<OrderSagaEntity> findBySalesOrderId(UUID salesOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT saga FROM OrderSagaEntity saga WHERE saga.salesOrderId = :salesOrderId")
    Optional<OrderSagaEntity> findBySalesOrderIdForUpdate(UUID salesOrderId);

    @Query("""
            SELECT saga.salesOrderId
            FROM OrderSagaEntity saga
            WHERE (saga.status = 'RETRY_SCHEDULED' AND saga.nextAttemptAt <= :now)
               OR (saga.status = 'PROCESSING' AND saga.executionStartedAt < :staleBefore)
            ORDER BY saga.updatedAt, saga.salesOrderId
            """)
    List<UUID> findRecoverableOrderIds(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );
}
