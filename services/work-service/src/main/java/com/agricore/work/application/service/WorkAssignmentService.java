package com.agricore.work.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.work.api.response.WorkAssignmentResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.WorkAssignmentJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.WorkAssignmentEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WorkAssignmentService {

    private final WorkAssignmentJpaRepository assignmentRepository;
    private final WorkTaskJpaRepository taskRepository;
    private final WorkAccessGuard accessGuard;

    public WorkAssignmentService(
            WorkAssignmentJpaRepository assignmentRepository,
            WorkTaskJpaRepository taskRepository,
            WorkAccessGuard accessGuard
    ) {
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(WorkTaskEntity task, String assignedBy) {
        WorkAssignmentEntity assignment = new WorkAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setWorkTaskId(task.getId());
        assignment.setEmployeeId(task.getAssignedEmployeeId());
        assignment.setAssignedBy(AuthenticatedActor.requireValid(assignedBy));
        assignment.setAssignedAt(Instant.now());
        assignment.setTaskVersion(task.getVersion());
        assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkAssignmentResponse> list(UUID taskId, Pageable pageable) {
        WorkTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
        accessGuard.requirePlot(task.getPlotId());
        Page<WorkAssignmentEntity> page = assignmentRepository.findByWorkTaskId(taskId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private WorkAssignmentResponse toResponse(WorkAssignmentEntity assignment) {
        return new WorkAssignmentResponse(
                assignment.getId(),
                assignment.getWorkTaskId(),
                assignment.getEmployeeId(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt(),
                assignment.getTaskVersion()
        );
    }
}
