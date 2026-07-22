package com.agricore.cropcatalog.infrastructure.persistence;

import com.agricore.cropcatalog.infrastructure.persistence.entity.CropVarietyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CropVarietyJpaRepository extends JpaRepository<CropVarietyEntity, UUID> {

    Page<CropVarietyEntity> findByCropId(UUID cropId, Pageable pageable);

    @Query("""
            SELECT variety FROM CropVarietyEntity variety
            WHERE variety.cropId = :cropId
              AND (
                LOWER(variety.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(variety.code) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<CropVarietyEntity> searchByCropId(
            @Param("cropId") UUID cropId,
            @Param("query") String query,
            Pageable pageable
    );
}
