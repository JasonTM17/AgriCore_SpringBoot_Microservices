package com.agricore.cropcycle.infrastructure.persistence;

import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleStageHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CropCycleStageHistoryJpaRepository
        extends JpaRepository<CropCycleStageHistoryEntity, UUID> {

    Page<CropCycleStageHistoryEntity> findByCropCycleId(UUID cropCycleId, Pageable pageable);

    long countByCropCycleId(UUID cropCycleId);
}
