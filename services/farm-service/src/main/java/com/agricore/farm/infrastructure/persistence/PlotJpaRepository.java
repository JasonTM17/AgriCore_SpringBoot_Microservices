package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.PlotStatus;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlotJpaRepository extends JpaRepository<PlotEntity, UUID> {
    boolean existsByFarmIdAndCodeIgnoreCase(UUID farmId, String code);
    boolean existsByFarmIdAndAreaId(UUID farmId, UUID areaId);

    @Query("""
            SELECT p FROM PlotEntity p
            WHERE p.farmId = :farmId
              AND (:status IS NULL OR p.status = :status)
              AND (:areaId IS NULL OR p.areaId = :areaId)
              AND (
                :query IS NULL
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<PlotEntity> searchByFarm(
            @Param("farmId") UUID farmId,
            @Param("status") PlotStatus status,
            @Param("areaId") UUID areaId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM PlotEntity p
            WHERE p.id = :plotId
              AND EXISTS (
                  SELECT m.id FROM FarmMembershipEntity m
                  WHERE m.farmId = p.farmId AND m.subject = :subject
              )
            """)
    Optional<PlotEntity> findAccessibleById(
            @Param("plotId") UUID plotId,
            @Param("subject") String subject
    );
}
