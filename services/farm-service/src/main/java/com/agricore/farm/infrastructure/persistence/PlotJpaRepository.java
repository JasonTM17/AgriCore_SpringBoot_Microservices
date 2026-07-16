package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlotJpaRepository extends JpaRepository<PlotEntity, UUID> {
    boolean existsByFarmIdAndCodeIgnoreCase(UUID farmId, String code);
    Page<PlotEntity> findByFarmId(UUID farmId, Pageable pageable);
}
