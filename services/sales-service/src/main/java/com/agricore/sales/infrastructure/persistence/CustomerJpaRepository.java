package com.agricore.sales.infrastructure.persistence;

import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
