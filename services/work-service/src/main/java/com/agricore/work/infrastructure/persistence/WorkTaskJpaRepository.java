package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkTaskJpaRepository extends JpaRepository<WorkTaskEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Page<WorkTaskEntity> findByCropCycleId(UUID cropCycleId, Pageable pageable);
}
