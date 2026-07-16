package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrderSagaJpaRepository extends JpaRepository<OrderSagaEntity, UUID> {
    Optional<OrderSagaEntity> findBySalesOrderId(UUID salesOrderId);
}
