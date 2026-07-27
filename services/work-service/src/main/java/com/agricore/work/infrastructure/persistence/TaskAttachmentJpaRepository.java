package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAttachmentJpaRepository extends JpaRepository<TaskAttachmentEntity, UUID> {

    Optional<TaskAttachmentEntity> findByWorkTaskIdAndSha256(UUID workTaskId, String sha256);

    Optional<TaskAttachmentEntity> findByIdAndWorkTaskId(UUID id, UUID workTaskId);

    long countByWorkTaskId(UUID workTaskId);

    List<TaskAttachmentEntity> findByWorkTaskIdOrderByUploadedAtAscIdAsc(UUID workTaskId);

    List<TaskAttachmentEntity> findByWorkTaskIdInOrderByUploadedAtAscIdAsc(Collection<UUID> workTaskIds);
}
