package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.SoilProfileStatus;
import com.agricore.farm.infrastructure.persistence.entity.SoilProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SoilProfileJpaRepository extends JpaRepository<SoilProfileEntity, UUID> {

    boolean existsByFarmIdAndPlotIdAndSampleCodeIgnoreCase(UUID farmId, UUID plotId, String sampleCode);

    Optional<SoilProfileEntity> findByFarmIdAndPlotIdAndId(UUID farmId, UUID plotId, UUID id);

    @Query("""
            SELECT profile FROM SoilProfileEntity profile
            WHERE profile.farmId = :farmId
              AND profile.plotId = :plotId
              AND (:status IS NULL OR profile.status = :status)
              AND (:sampledFrom IS NULL OR profile.sampledAt >= :sampledFrom)
              AND (:sampledTo IS NULL OR profile.sampledAt <= :sampledTo)
              AND (
                :query = ''
                OR LOWER(profile.sampleCode) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
              )
            """)
    Page<SoilProfileEntity> searchByPlot(
            @Param("farmId") UUID farmId,
            @Param("plotId") UUID plotId,
            @Param("status") SoilProfileStatus status,
            @Param("sampledFrom") LocalDate sampledFrom,
            @Param("sampledTo") LocalDate sampledTo,
            @Param("query") String query,
            Pageable pageable
    );
}
