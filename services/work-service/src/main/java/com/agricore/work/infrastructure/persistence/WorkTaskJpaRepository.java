package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkTaskJpaRepository extends JpaRepository<WorkTaskEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Page<WorkTaskEntity> findByCropCycleId(UUID cropCycleId, Pageable pageable);
    Page<WorkTaskEntity> findByPlotId(UUID plotId, Pageable pageable);
    Page<WorkTaskEntity> findByCropCycleIdAndPlotId(UUID cropCycleId, UUID plotId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM WorkTaskEntity task WHERE task.id = :id")
    Optional<WorkTaskEntity> findByIdForUpdate(@Param("id") UUID id);
}
