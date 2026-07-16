package com.agricore.harvest.infrastructure.persistence;

import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface HarvestBatchJpaRepository extends JpaRepository<HarvestBatchEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
