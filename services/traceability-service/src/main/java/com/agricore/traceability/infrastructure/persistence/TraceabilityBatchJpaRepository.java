package com.agricore.traceability.infrastructure.persistence;

import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TraceabilityBatchJpaRepository extends JpaRepository<TraceabilityBatchEntity, UUID> {
    Optional<TraceabilityBatchEntity> findByTraceabilityCode(String code);
    Optional<TraceabilityBatchEntity> findFirstByHarvestBatchId(UUID harvestBatchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from TraceabilityBatchEntity batch where batch.harvestBatchId = :harvestBatchId")
    Optional<TraceabilityBatchEntity> findByHarvestBatchIdForUpdate(@Param("harvestBatchId") UUID harvestBatchId);
}
