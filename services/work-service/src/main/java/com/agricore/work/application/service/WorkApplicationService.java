package com.agricore.work.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.work.api.request.AssignTaskRequest;
import com.agricore.work.api.request.CancelTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.domain.model.TaskType;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkApplicationService {

    private final WorkTaskJpaRepository taskRepository;
    private final WorkAccessGuard accessGuard;
    private final WorkEventOutboxWriter eventWriter;
    private final WorkTaskLifecycleService lifecycleService;
    private final WorkTaskCompletionService completionService;
    private final WorkTaskResponseAssembler responseAssembler;

    public WorkApplicationService(
            WorkTaskJpaRepository taskRepository,
            WorkAccessGuard accessGuard,
            WorkEventOutboxWriter eventWriter,
            WorkTaskLifecycleService lifecycleService,
            WorkTaskCompletionService completionService,
            WorkTaskResponseAssembler responseAssembler
    ) {
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
        this.lifecycleService = lifecycleService;
        this.completionService = completionService;
        this.responseAssembler = responseAssembler;
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
        return responseAssembler.toResponse(task);
    }

    public WorkTaskResponse assign(UUID taskId, AssignTaskRequest request, String assignedBy) {
        String actor = AuthenticatedActor.requireValid(assignedBy);
        return responseAssembler.toResponse(lifecycleService.assign(taskId, request.assignedEmployeeId(), actor));
    }

    public WorkTaskResponse start(UUID taskId, String executedBy) {
        String actor = AuthenticatedActor.requireValid(executedBy);
        return responseAssembler.toResponse(lifecycleService.start(taskId, actor));
    }

    public WorkTaskResponse cancel(UUID taskId, CancelTaskRequest request, String executedBy) {
        String actor = AuthenticatedActor.requireValid(executedBy);
        return responseAssembler.toResponse(lifecycleService.cancel(taskId, request.notes(), actor));
    }

    public WorkTaskResponse complete(UUID taskId, CompleteTaskRequest request, String executedBy) {
        String actor = AuthenticatedActor.requireValid(executedBy);
        return responseAssembler.toResponse(completionService.complete(taskId, request, actor));
    }

    @Transactional(readOnly = true)
    public WorkTaskResponse get(UUID id) {
        return responseAssembler.toResponse(require(id));
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
        return PageResponse.of(
                responseAssembler.toResponses(page.getContent()),
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

}
