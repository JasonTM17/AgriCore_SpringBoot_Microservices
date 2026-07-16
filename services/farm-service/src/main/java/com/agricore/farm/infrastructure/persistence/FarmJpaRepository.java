package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FarmJpaRepository extends JpaRepository<FarmEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<FarmEntity> findByCodeIgnoreCase(String code);
    Page<FarmEntity> findByStatus(FarmStatus status, Pageable pageable);
    Page<FarmEntity> findByProvinceIgnoreCaseContaining(String province, Pageable pageable);
}
