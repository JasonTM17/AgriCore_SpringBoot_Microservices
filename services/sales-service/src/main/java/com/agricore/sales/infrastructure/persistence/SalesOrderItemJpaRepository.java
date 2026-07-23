package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.SalesOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesOrderItemJpaRepository extends JpaRepository<SalesOrderItemEntity, UUID> {
    List<SalesOrderItemEntity> findAllBySalesOrderIdOrderByLineNumber(UUID salesOrderId);
}
