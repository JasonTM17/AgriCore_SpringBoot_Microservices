package com.agricore.work.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.work.api.request.AssignTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.response.WorkTaskResponse;
import com.agricore.work.application.service.WorkApplicationService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-tasks")
@Validated
public class WorkTaskController {

    private final WorkApplicationService service;

    public WorkTaskController(WorkApplicationService service) {
        this.service = service;
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
    public WorkTaskResponse assign(@PathVariable UUID taskId, @Valid @RequestBody AssignTaskRequest request) {
        return service.assign(taskId, request);
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','FIELD_WORKER')")
    public WorkTaskResponse complete(
            @PathVariable UUID taskId,
            @Valid @RequestBody(required = false) CompleteTaskRequest request
    ) {
        return service.complete(taskId, request == null ? new CompleteTaskRequest(null) : request);
    }
}
