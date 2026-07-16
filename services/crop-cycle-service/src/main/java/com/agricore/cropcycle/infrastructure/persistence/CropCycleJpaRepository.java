package com.agricore.cropcycle.infrastructure.persistence;

import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CropCycleJpaRepository extends JpaRepository<CropCycleEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Page<CropCycleEntity> findByFarmId(UUID farmId, Pageable pageable);
    Page<CropCycleEntity> findByPlotId(UUID plotId, Pageable pageable);
}
