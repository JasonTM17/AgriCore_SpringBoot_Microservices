package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.FarmAreaStatus;
import com.agricore.farm.infrastructure.persistence.entity.FarmAreaEntity;
import com.agricore.farm.infrastructure.persistence.entity.FarmAreaKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FarmAreaJpaRepository extends JpaRepository<FarmAreaEntity, FarmAreaKey> {

    boolean existsByFarmIdAndId(UUID farmId, UUID id);

    boolean existsByFarmIdAndCodeIgnoreCase(UUID farmId, String code);

    Optional<FarmAreaEntity> findByFarmIdAndId(UUID farmId, UUID id);

    @Query("""
            SELECT a FROM FarmAreaEntity a
            WHERE a.farmId = :farmId
              AND (:status IS NULL OR a.status = :status)
              AND (
                :query = ''
                OR LOWER(a.code) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<FarmAreaEntity> searchByFarm(
            @Param("farmId") UUID farmId,
            @Param("status") FarmAreaStatus status,
            @Param("query") String query,
            Pageable pageable
    );
}
