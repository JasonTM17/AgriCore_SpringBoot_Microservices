package com.agricore.work.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.work.api.request.AssignTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.domain.model.TaskType;
import com.agricore.work.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkApplicationService {

    private final WorkTaskJpaRepository taskRepository;
    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final WorkAccessGuard accessGuard;

    public WorkApplicationService(
            WorkTaskJpaRepository taskRepository,
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            WorkAccessGuard accessGuard
    ) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
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
        enqueue(EventTypes.WORK_TASK_CREATED, task);
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
        taskRepository.save(task);
        enqueue(EventTypes.WORK_TASK_ASSIGNED, task);
        return toResponse(task);
    }

    @Transactional
    public WorkTaskResponse complete(UUID taskId, CompleteTaskRequest request) {
        WorkTaskEntity task = require(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return toResponse(task);
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new WorkException("TASK_CANCELLED", "Cannot complete a cancelled task", 409);
        }
        Instant now = Instant.now();
        if (task.getActualStart() == null) {
            task.setActualStart(now);
        }
        task.setActualEnd(now);
        task.setStatus(TaskStatus.COMPLETED);
        if (request != null && request.notes() != null) {
            task.setNotes(request.notes());
        }
        task.setUpdatedAt(now);
        taskRepository.save(task);
        enqueue(EventTypes.WORK_TASK_COMPLETED, task);
        return toResponse(task);
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
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
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

    private void enqueue(String eventType, WorkTaskEntity task) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", task.getId().toString());
            payload.put("code", task.getCode());
            payload.put("cropCycleId", task.getCropCycleId().toString());
            payload.put("plotId", task.getPlotId().toString());
            payload.put("taskType", task.getTaskType().name());
            payload.put("status", task.getStatus().name());
            if (task.getAssignedEmployeeId() != null) {
                payload.put("assignedEmployeeId", task.getAssignedEmployeeId().toString());
            }
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("producer", "work-service");
            envelope.set("payload", payload);
            outboxRepository.save(OutboxEventEntity.create(
                    "WorkTask", task.getId().toString(), eventType,
                    "agricore.work.events", objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new WorkException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }

    private WorkTaskResponse toResponse(WorkTaskEntity t) {
        return new WorkTaskResponse(
                t.getId(), t.getCode(), t.getCropCycleId(), t.getPlotId(), t.getTaskType().name(),
                t.getTitle(), t.getDescription(), t.getPriority(), t.getAssignedEmployeeId(),
                t.getScheduledStart(), t.getScheduledEnd(), t.getActualStart(), t.getActualEnd(),
                t.getStatus().name(), t.getNotes(), t.getCreatedAt(), t.getVersion()
        );
    }
}
