package com.agricore.work.application.service;

import com.agricore.work.api.response.MaterialUsageResponse;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.TaskAttachmentJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class WorkTaskResponseAssembler {

    private final MaterialUsageJpaRepository materialUsageRepository;
    private final TaskAttachmentJpaRepository attachmentRepository;

    WorkTaskResponseAssembler(
            MaterialUsageJpaRepository materialUsageRepository,
            TaskAttachmentJpaRepository attachmentRepository
    ) {
        this.materialUsageRepository = materialUsageRepository;
        this.attachmentRepository = attachmentRepository;
    }

    WorkTaskResponse toResponse(WorkTaskEntity task) {
        return toResponses(List.of(task)).getFirst();
    }

    List<WorkTaskResponse> toResponses(List<WorkTaskEntity> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<UUID> taskIds = tasks.stream().map(WorkTaskEntity::getId).toList();
        Map<UUID, List<MaterialUsageEntity>> usagesByTask = materialUsageRepository
                .findByWorkTaskIdInOrderByCreatedAtAsc(taskIds)
                .stream()
                .collect(Collectors.groupingBy(MaterialUsageEntity::getWorkTaskId));
        Map<UUID, List<TaskAttachmentEntity>> attachmentsByTask = attachmentRepository
                .findByWorkTaskIdInOrderByUploadedAtAscIdAsc(taskIds)
                .stream()
                .collect(Collectors.groupingBy(TaskAttachmentEntity::getWorkTaskId));
        return tasks.stream()
                .map(task -> toResponse(
                        task,
                        usagesByTask.getOrDefault(task.getId(), List.of()),
                        attachmentsByTask.getOrDefault(task.getId(), List.of())
                ))
                .toList();
    }

    private WorkTaskResponse toResponse(
            WorkTaskEntity task,
            List<MaterialUsageEntity> usages,
            List<TaskAttachmentEntity> attachments
    ) {
        return new WorkTaskResponse(
                task.getId(), task.getCode(), task.getCropCycleId(), task.getPlotId(), task.getTaskType().name(),
                task.getTitle(), task.getDescription(), task.getPriority(), task.getAssignedEmployeeId(),
                task.getScheduledStart(), task.getScheduledEnd(), task.getActualStart(), task.getActualEnd(),
                task.getStatus().name(), task.getNotes(), task.getCreatedAt(), task.getVersion(),
                usages.stream().map(this::toMaterialUsageResponse).toList(),
                attachments.stream().map(TaskAttachmentResponses::from).toList()
        );
    }

    private MaterialUsageResponse toMaterialUsageResponse(MaterialUsageEntity usage) {
        return new MaterialUsageResponse(
                usage.getId(),
                usage.getInventoryItemId(),
                usage.getQuantity(),
                usage.getUnit(),
                usage.getStatus().name(),
                usage.getInventoryReferenceId(),
                usage.getLastError(),
                usage.getConsumedAt()
        );
    }
}
