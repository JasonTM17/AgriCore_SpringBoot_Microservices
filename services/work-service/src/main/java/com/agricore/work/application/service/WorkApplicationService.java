package com.agricore.work.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.work.api.request.AssignTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.api.response.MaterialUsageResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.domain.model.TaskType;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkApplicationService {

    private final WorkTaskJpaRepository taskRepository;
    private final WorkAccessGuard accessGuard;
    private final WorkEventOutboxWriter eventWriter;
    private final MaterialUsageJpaRepository materialUsageRepository;
    private final WorkTaskCompletionService completionService;

    public WorkApplicationService(
            WorkTaskJpaRepository taskRepository,
            WorkAccessGuard accessGuard,
            WorkEventOutboxWriter eventWriter,
            MaterialUsageJpaRepository materialUsageRepository,
            WorkTaskCompletionService completionService
    ) {
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
        this.materialUsageRepository = materialUsageRepository;
        this.completionService = completionService;
    }

    @Transactional
    public WorkTaskResponse create(CreateWorkTaskRequest request) {
        accessGuard.requirePlot(request.plotId());
        String code = request.code().trim().toUpperCase();
        if (taskRepository.existsByCodeIgnoreCase(code)) {
            throw new WorkException("TASK_CODE_EXISTS", "Task code already exists", 409);
        }
        TaskType type;
        try {
            type = TaskType.valueOf(request.taskType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WorkException("INVALID_TASK_TYPE", "Unknown task type: " + request.taskType(), 400);
        }

        Instant now = Instant.now();
        WorkTaskEntity task = new WorkTaskEntity();
        task.setId(UUID.randomUUID());
        task.setCode(code);
        task.setCropCycleId(request.cropCycleId());
        task.setPlotId(request.plotId());
        task.setTaskType(type);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority().trim().toUpperCase());
        task.setScheduledStart(request.scheduledStart());
        task.setScheduledEnd(request.scheduledEnd());
        task.setStatus(TaskStatus.CREATED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);
        eventWriter.workTask(EventTypes.WORK_TASK_CREATED, task);
        return toResponse(task);
    }

    @Transactional
    public WorkTaskResponse assign(UUID taskId, AssignTaskRequest request) {
        WorkTaskEntity task = require(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new WorkException("TASK_TERMINAL", "Cannot assign a terminal task", 409);
        }
        task.setAssignedEmployeeId(request.assignedEmployeeId());
        task.setStatus(TaskStatus.ASSIGNED);
        task.setUpdatedAt(Instant.now());
        task = taskRepository.saveAndFlush(task);
        eventWriter.workTask(EventTypes.WORK_TASK_ASSIGNED, task);
        return toResponse(task);
    }

    public WorkTaskResponse complete(UUID taskId, CompleteTaskRequest request) {
        return toResponse(completionService.complete(taskId, request));
    }

    @Transactional(readOnly = true)
    public WorkTaskResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkTaskResponse> list(UUID cropCycleId, UUID plotId, Pageable pageable) {
        accessGuard.requireListScope(plotId);
        Page<WorkTaskEntity> page;
        if (cropCycleId != null && plotId != null) {
            page = taskRepository.findByCropCycleIdAndPlotId(cropCycleId, plotId, pageable);
        } else if (plotId != null) {
            page = taskRepository.findByPlotId(plotId, pageable);
        } else if (cropCycleId != null) {
            page = taskRepository.findByCropCycleId(cropCycleId, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }
        Map<UUID, List<MaterialUsageEntity>> usagesByTask = page.isEmpty()
                ? Map.of()
                : materialUsageRepository
                        .findByWorkTaskIdInOrderByCreatedAtAsc(
                                page.getContent().stream().map(WorkTaskEntity::getId).toList()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(MaterialUsageEntity::getWorkTaskId));
        return PageResponse.of(
                page.getContent().stream()
                        .map(task -> toResponse(task, usagesByTask.getOrDefault(task.getId(), List.of())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private WorkTaskEntity require(UUID id) {
        WorkTaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
        accessGuard.requirePlot(task.getPlotId());
        return task;
    }

    private WorkTaskResponse toResponse(WorkTaskEntity t) {
        return toResponse(t, materialUsageRepository.findByWorkTaskIdOrderByCreatedAtAsc(t.getId()));
    }

    private WorkTaskResponse toResponse(WorkTaskEntity t, List<MaterialUsageEntity> usages) {
        return new WorkTaskResponse(
                t.getId(), t.getCode(), t.getCropCycleId(), t.getPlotId(), t.getTaskType().name(),
                t.getTitle(), t.getDescription(), t.getPriority(), t.getAssignedEmployeeId(),
                t.getScheduledStart(), t.getScheduledEnd(), t.getActualStart(), t.getActualEnd(),
                t.getStatus().name(), t.getNotes(), t.getCreatedAt(), t.getVersion(),
                usages.stream().map(this::toMaterialUsageResponse).toList()
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
