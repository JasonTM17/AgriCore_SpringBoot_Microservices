package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.WorkAssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkAssignmentJpaRepository extends JpaRepository<WorkAssignmentEntity, UUID> {

    Page<WorkAssignmentEntity> findByWorkTaskId(UUID workTaskId, Pageable pageable);
}
