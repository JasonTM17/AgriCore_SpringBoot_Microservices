package com.agricore.cropcycle.infrastructure.persistence;

import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleObservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CropCycleObservationJpaRepository
        extends JpaRepository<CropCycleObservationEntity, UUID> {

    Page<CropCycleObservationEntity> findByCropCycleId(UUID cropCycleId, Pageable pageable);

    long countByCropCycleId(UUID cropCycleId);
}
