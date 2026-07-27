package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.infrastructure.persistence.TaskAttachmentJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import com.agricore.work.infrastructure.storage.ObjectStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class TaskAttachmentStateService {

    private final TaskAttachmentJpaRepository attachmentRepository;
    private final WorkTaskJpaRepository taskRepository;
    private final WorkAccessGuard accessGuard;
    private final int maxAttachmentsPerTask;

    TaskAttachmentStateService(
            TaskAttachmentJpaRepository attachmentRepository,
            WorkTaskJpaRepository taskRepository,
            WorkAccessGuard accessGuard,
            ObjectStorageProperties properties
    ) {
        this.attachmentRepository = attachmentRepository;
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.maxAttachmentsPerTask = properties.validatedMaxAttachmentsPerTask();
    }

    @Transactional(readOnly = true)
    public Optional<TaskAttachmentEntity> validateUpload(UUID taskId, String sha256) {
        WorkTaskEntity task = requireTask(taskId);
        Optional<TaskAttachmentEntity> existing = attachmentRepository.findByWorkTaskIdAndSha256(taskId, sha256);
        if (existing.isPresent()) {
            return existing;
        }
        requireAttachable(task);
        requireAvailableSlot(taskId);
        return Optional.empty();
    }

    @Transactional
    public TaskAttachmentEntity persist(UUID taskId, TaskAttachmentEntity candidate) {
        WorkTaskEntity task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(TaskAttachmentStateService::taskNotFound);
        accessGuard.requirePlot(task.getPlotId());
        Optional<TaskAttachmentEntity> existing = attachmentRepository
                .findByWorkTaskIdAndSha256(taskId, candidate.getSha256());
        if (existing.isPresent()) {
            return existing.get();
        }
        requireAttachable(task);
        requireAvailableSlot(taskId);
        return attachmentRepository.save(candidate);
    }

    @Transactional(readOnly = true)
    public Optional<TaskAttachmentEntity> findExisting(UUID taskId, String sha256) {
        requireTask(taskId);
        return attachmentRepository.findByWorkTaskIdAndSha256(taskId, sha256);
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentEntity> list(UUID taskId) {
        requireTask(taskId);
        return attachmentRepository.findByWorkTaskIdOrderByUploadedAtAscIdAsc(taskId);
    }

    @Transactional(readOnly = true)
    public TaskAttachmentEntity get(UUID taskId, UUID attachmentId) {
        requireTask(taskId);
        return attachmentRepository.findByIdAndWorkTaskId(attachmentId, taskId)
                .orElseThrow(() -> new WorkException(
                        "ATTACHMENT_NOT_FOUND",
                        "Task attachment not found",
                        404
                ));
    }

    private WorkTaskEntity requireTask(UUID taskId) {
        WorkTaskEntity task = taskRepository.findById(taskId).orElseThrow(TaskAttachmentStateService::taskNotFound);
        accessGuard.requirePlot(task.getPlotId());
        return task;
    }

    private void requireAvailableSlot(UUID taskId) {
        if (attachmentRepository.countByWorkTaskId(taskId) >= maxAttachmentsPerTask) {
            throw new WorkException(
                    "ATTACHMENT_LIMIT_REACHED",
                    "Task attachment limit reached",
                    409
            );
        }
    }

    private static void requireAttachable(WorkTaskEntity task) {
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new WorkException(
                    "TASK_ATTACHMENTS_LOCKED",
                    "Attachments cannot be added to a terminal task",
                    409
            );
        }
    }

    private static WorkException taskNotFound() {
        return new WorkException("TASK_NOT_FOUND", "Work task not found", 404);
    }
}
