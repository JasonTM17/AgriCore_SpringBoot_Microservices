package com.agricore.cropcycle.infrastructure.persistence;

import com.agricore.cropcycle.domain.model.CycleStatus;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CropCycleJpaRepository extends JpaRepository<CropCycleEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Page<CropCycleEntity> findByFarmId(UUID farmId, Pageable pageable);
    Page<CropCycleEntity> findByPlotId(UUID plotId, Pageable pageable);
    Page<CropCycleEntity> findByFarmIdAndPlotId(UUID farmId, UUID plotId, Pageable pageable);

    /**
     * Overlap when existing.plannedStart <= newEnd AND existing.plannedEnd >= newStart
     * (null plannedEnd treated as open-ended = far future).
     */
    @Query("""
            SELECT c FROM CropCycleEntity c
            WHERE c.plotId = :plotId
              AND c.status IN :activeStatuses
              AND c.plannedStartDate <= :newEnd
              AND COALESCE(c.plannedEndDate, :openEnd) >= :newStart
            """)
    List<CropCycleEntity> findOverlappingActiveCycles(
            @Param("plotId") UUID plotId,
            @Param("activeStatuses") Collection<CycleStatus> activeStatuses,
            @Param("newStart") LocalDate newStart,
            @Param("newEnd") LocalDate newEnd,
            @Param("openEnd") LocalDate openEnd
    );
}
