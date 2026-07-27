package com.agricore.harvest.infrastructure.persistence;

import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HarvestBatchJpaRepository extends JpaRepository<HarvestBatchEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM HarvestBatchEntity batch WHERE batch.id = :id")
    Optional<HarvestBatchEntity> findByIdForUpdate(@Param("id") UUID id);
}
