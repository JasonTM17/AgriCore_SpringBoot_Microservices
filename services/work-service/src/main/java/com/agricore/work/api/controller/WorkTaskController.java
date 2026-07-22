package com.agricore.work.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.work.api.request.AssignTaskRequest;
import com.agricore.work.api.request.CancelTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.response.TaskExecutionResponse;
import com.agricore.work.api.response.WorkAssignmentResponse;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.application.service.TaskExecutionService;
import com.agricore.work.application.service.WorkApplicationService;
import com.agricore.work.application.service.WorkAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-tasks")
@Validated
public class WorkTaskController {

    private final WorkApplicationService service;
    private final WorkAssignmentService assignmentService;
    private final TaskExecutionService executionService;

    public WorkTaskController(
            WorkApplicationService service,
            WorkAssignmentService assignmentService,
            TaskExecutionService executionService
    ) {
        this.service = service;
        this.assignmentService = assignmentService;
        this.executionService = executionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public ResponseEntity<WorkTaskResponse> create(@Valid @RequestBody CreateWorkTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<WorkTaskResponse> list(
            @RequestParam(required = false) UUID cropCycleId,
            @RequestParam(required = false) UUID plotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.list(cropCycleId, plotId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public WorkTaskResponse get(@PathVariable UUID taskId) {
        return service.get(taskId);
    }

    @PostMapping("/{taskId}/assign")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public WorkTaskResponse assign(
            @PathVariable UUID taskId,
            @Valid @RequestBody AssignTaskRequest request,
            Principal principal
    ) {
        return service.assign(taskId, request, principal.getName());
    }

    @GetMapping("/{taskId}/assignments")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<WorkAssignmentResponse> listAssignments(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(
                Sort.Order.desc("taskVersion"),
                Sort.Order.desc("assignedAt"),
                Sort.Order.desc("id")
        );
        return assignmentService.list(taskId, PageRequest.of(page, size, sort));
    }

    @PostMapping("/{taskId}/start")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','FIELD_WORKER')")
    public WorkTaskResponse start(@PathVariable UUID taskId, Principal principal) {
        return service.start(taskId, principal.getName());
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','FIELD_WORKER')")
    public WorkTaskResponse complete(
            @PathVariable UUID taskId,
            @Valid @RequestBody(required = false) CompleteTaskRequest request,
            Principal principal
    ) {
        return service.complete(
                taskId,
                request == null ? new CompleteTaskRequest(null) : request,
                principal.getName()
        );
    }

    @PostMapping("/{taskId}/cancel")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public WorkTaskResponse cancel(
            @PathVariable UUID taskId,
            @Valid @RequestBody(required = false) CancelTaskRequest request,
            Principal principal
    ) {
        return service.cancel(
                taskId,
                request == null ? new CancelTaskRequest(null) : request,
                principal.getName()
        );
    }

    @GetMapping("/{taskId}/executions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TaskExecutionResponse> listExecutions(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(
                Sort.Order.desc("taskVersion"),
                Sort.Order.desc("executedAt"),
                Sort.Order.desc("id")
        );
        return executionService.list(taskId, PageRequest.of(page, size, sort));
    }
}
