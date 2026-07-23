package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.IrrigationMethod;
import com.agricore.farm.domain.model.IrrigationZoneStatus;
import com.agricore.farm.infrastructure.persistence.entity.IrrigationZoneEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface IrrigationZoneJpaRepository extends JpaRepository<IrrigationZoneEntity, UUID> {

    boolean existsByFarmIdAndPlotIdAndCodeIgnoreCase(UUID farmId, UUID plotId, String code);

    Optional<IrrigationZoneEntity> findByFarmIdAndPlotIdAndId(
            UUID farmId,
            UUID plotId,
            UUID id
    );

    @Query("""
            SELECT zone
            FROM IrrigationZoneEntity zone
            WHERE zone.farmId = :farmId
              AND zone.plotId = :plotId
              AND (:status IS NULL OR zone.status = :status)
              AND (:method IS NULL OR zone.method = :method)
              AND (
                :query IS NULL
                OR LOWER(zone.code) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR LOWER(zone.name) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
              )
            """)
    Page<IrrigationZoneEntity> searchByPlot(
            UUID farmId,
            UUID plotId,
            IrrigationZoneStatus status,
            IrrigationMethod method,
            String query,
            Pageable pageable
    );
}
