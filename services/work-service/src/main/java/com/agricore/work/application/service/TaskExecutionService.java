package com.agricore.work.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.work.api.response.TaskExecutionResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskExecutionAction;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.infrastructure.persistence.TaskExecutionJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.TaskExecutionEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskExecutionService {

    private final TaskExecutionJpaRepository executionRepository;
    private final WorkTaskJpaRepository taskRepository;
    private final WorkAccessGuard accessGuard;

    public TaskExecutionService(
            TaskExecutionJpaRepository executionRepository,
            WorkTaskJpaRepository taskRepository,
            WorkAccessGuard accessGuard
    ) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            WorkTaskEntity task,
            TaskExecutionAction action,
            TaskStatus previousStatus,
            String notes,
            String executedBy
    ) {
        TaskExecutionEntity execution = new TaskExecutionEntity();
        execution.setWorkTaskId(task.getId());
        execution.setAction(action);
        execution.setPreviousStatus(previousStatus);
        execution.setStatus(task.getStatus());
        execution.setNotes(notes);
        execution.setExecutedBy(AuthenticatedActor.requireValid(executedBy));
        execution.setExecutedAt(Instant.now());
        execution.setTaskVersion(task.getVersion());
        executionRepository.save(execution);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskExecutionResponse> list(UUID taskId, Pageable pageable) {
        WorkTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
        accessGuard.requirePlot(task.getPlotId());
        Page<TaskExecutionEntity> page = executionRepository.findByWorkTaskId(taskId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private TaskExecutionResponse toResponse(TaskExecutionEntity execution) {
        return new TaskExecutionResponse(
                execution.getId(),
                execution.getWorkTaskId(),
                execution.getAction().name(),
                execution.getPreviousStatus() == null ? null : execution.getPreviousStatus().name(),
                execution.getStatus().name(),
                execution.getNotes(),
                execution.getExecutedBy(),
                execution.getExecutedAt(),
                execution.getTaskVersion()
        );
    }
}
