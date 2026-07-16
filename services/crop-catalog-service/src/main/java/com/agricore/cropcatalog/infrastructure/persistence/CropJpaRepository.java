package com.agricore.cropcatalog.infrastructure.persistence;

import com.agricore.cropcatalog.infrastructure.persistence.entity.CropEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CropJpaRepository extends JpaRepository<CropEntity, UUID> {
    Optional<CropEntity> findByCodeIgnoreCase(String code);
    Page<CropEntity> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<CropEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
