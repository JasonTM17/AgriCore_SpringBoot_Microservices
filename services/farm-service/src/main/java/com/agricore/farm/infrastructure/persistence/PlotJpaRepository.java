package com.agricore.farm.infrastructure.persistence;

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
    Page<PlotEntity> findByFarmId(UUID farmId, Pageable pageable);

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
