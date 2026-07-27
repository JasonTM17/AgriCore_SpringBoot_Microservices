package com.agricore.work.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskExecutionAction;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkTaskLifecycleService {

    private final WorkTaskJpaRepository taskRepository;
    private final MaterialUsageJpaRepository materialUsageRepository;
    private final WorkAccessGuard accessGuard;
    private final WorkEventOutboxWriter eventWriter;
    private final WorkAssignmentService assignmentService;
    private final TaskExecutionService executionService;

    public WorkTaskLifecycleService(
            WorkTaskJpaRepository taskRepository,
            MaterialUsageJpaRepository materialUsageRepository,
            WorkAccessGuard accessGuard,
            WorkEventOutboxWriter eventWriter,
            WorkAssignmentService assignmentService,
            TaskExecutionService executionService
    ) {
        this.taskRepository = taskRepository;
        this.materialUsageRepository = materialUsageRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
        this.assignmentService = assignmentService;
        this.executionService = executionService;
    }

    @Transactional
    public WorkTaskEntity assign(UUID taskId, UUID assignedEmployeeId, String assignedBy) {
        WorkTaskEntity task = requireForUpdate(taskId);
        if (task.getStatus() != TaskStatus.CREATED
                && task.getStatus() != TaskStatus.ASSIGNED
                && task.getStatus() != TaskStatus.OVERDUE) {
            throw invalidTransition(task, "assign");
        }
        if (task.getStatus() == TaskStatus.ASSIGNED
                && Objects.equals(task.getAssignedEmployeeId(), assignedEmployeeId)) {
            return task;
        }

        task.setAssignedEmployeeId(assignedEmployeeId);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setUpdatedAt(Instant.now());
        task = taskRepository.saveAndFlush(task);
        assignmentService.record(task, assignedBy);
        eventWriter.workTask(EventTypes.WORK_TASK_ASSIGNED, task);
        return task;
    }

    @Transactional
    public WorkTaskEntity start(UUID taskId, String executedBy) {
        WorkTaskEntity task = requireForUpdate(taskId);
        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            return task;
        }
        if (task.getStatus() != TaskStatus.ASSIGNED && task.getStatus() != TaskStatus.OVERDUE) {
            throw invalidTransition(task, "start");
        }
        if (task.getAssignedEmployeeId() == null) {
            throw new WorkException("TASK_UNASSIGNED", "Cannot start a task without an assignee", 409);
        }

        TaskStatus previousStatus = task.getStatus();
        Instant now = Instant.now();
        task.setActualStart(now);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(now);
        task = taskRepository.saveAndFlush(task);
        executionService.record(task, TaskExecutionAction.STARTED, previousStatus, null, executedBy);
        return task;
    }

    @Transactional
    public WorkTaskEntity cancel(UUID taskId, String notes, String executedBy) {
        WorkTaskEntity task = requireForUpdate(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return task;
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw invalidTransition(task, "cancel");
        }
        if (materialUsageRepository.existsByWorkTaskId(taskId)) {
            throw new WorkException(
                    "TASK_MATERIAL_PROCESSING",
                    "Cannot cancel a task after material processing has started",
                    409
            );
        }

        TaskStatus previousStatus = task.getStatus();
        Instant now = Instant.now();
        if (task.getActualStart() != null) {
            task.setActualEnd(now);
        }
        task.setStatus(TaskStatus.CANCELLED);
        if (notes != null) {
            task.setNotes(notes);
        }
        task.setUpdatedAt(now);
        task = taskRepository.saveAndFlush(task);
        executionService.record(task, TaskExecutionAction.CANCELLED, previousStatus, notes, executedBy);
        return task;
    }

    private WorkTaskEntity requireForUpdate(UUID taskId) {
        WorkTaskEntity task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
        accessGuard.requirePlot(task.getPlotId());
        return task;
    }

    private WorkException invalidTransition(WorkTaskEntity task, String action) {
        return new WorkException(
                "INVALID_TASK_TRANSITION",
                "Cannot " + action + " task from " + task.getStatus().name(),
                409
        );
    }
}
