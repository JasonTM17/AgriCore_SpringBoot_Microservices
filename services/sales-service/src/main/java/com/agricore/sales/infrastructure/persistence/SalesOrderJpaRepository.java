package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SalesOrderJpaRepository extends JpaRepository<SalesOrderEntity, UUID> {
    boolean existsByOrderNumberIgnoreCase(String orderNumber);
}
