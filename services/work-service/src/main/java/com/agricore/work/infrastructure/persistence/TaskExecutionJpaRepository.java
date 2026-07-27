package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.TaskExecutionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskExecutionJpaRepository extends JpaRepository<TaskExecutionEntity, Long> {

    Page<TaskExecutionEntity> findByWorkTaskId(UUID workTaskId, Pageable pageable);
}
