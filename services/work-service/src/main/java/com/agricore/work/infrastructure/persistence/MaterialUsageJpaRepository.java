package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialUsageJpaRepository extends JpaRepository<MaterialUsageEntity, UUID> {

    List<MaterialUsageEntity> findByWorkTaskIdOrderByCreatedAtAsc(UUID workTaskId);

    List<MaterialUsageEntity> findByWorkTaskIdInOrderByCreatedAtAsc(Collection<UUID> workTaskIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT usage FROM MaterialUsageEntity usage WHERE usage.id = :id")
    Optional<MaterialUsageEntity> findByIdForUpdate(@Param("id") UUID id);
}
