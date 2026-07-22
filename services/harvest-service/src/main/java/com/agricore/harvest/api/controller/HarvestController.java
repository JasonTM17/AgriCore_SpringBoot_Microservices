package com.agricore.harvest.api.controller;

import com.agricore.harvest.api.request.CompleteHarvestBatchRequest;
import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.request.StartHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.application.service.HarvestApplicationService;
import com.agricore.harvest.application.service.HarvestCompletionEventRepairService;
import com.agricore.harvest.application.service.HarvestCompletionEventStatusService;
import com.agricore.harvest.application.service.HarvestLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/harvests")
public class HarvestController {

    private final HarvestApplicationService service;
    private final HarvestLifecycleService lifecycleService;
    private final HarvestCompletionEventStatusService eventStatusService;
    private final HarvestCompletionEventRepairService eventRepairService;

    public HarvestController(
            HarvestApplicationService service,
            HarvestLifecycleService lifecycleService,
            HarvestCompletionEventStatusService eventStatusService,
            HarvestCompletionEventRepairService eventRepairService
    ) {
        this.service = service;
        this.lifecycleService = lifecycleService;
        this.eventStatusService = eventStatusService;
        this.eventRepairService = eventRepairService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER')")
    public ResponseEntity<HarvestBatchResponse> start(@Valid @RequestBody StartHarvestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lifecycleService.start(request));
    }

    @PostMapping("/{harvestId}/complete")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER')")
    public HarvestBatchResponse complete(
            @PathVariable UUID harvestId,
            @Valid @RequestBody CompleteHarvestBatchRequest request
    ) {
        return lifecycleService.complete(harvestId, request);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER')")
    public ResponseEntity<HarvestBatchResponse> complete(@Valid @RequestBody CompleteHarvestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.completeHarvest(request));
    }

    @GetMapping("/{harvestId}")
    @PreAuthorize("isAuthenticated()")
    public HarvestBatchResponse get(@PathVariable UUID harvestId) {
        return service.get(harvestId);
    }

    @GetMapping("/{harvestId}/completion-event")
    @PreAuthorize("isAuthenticated()")
    public HarvestCompletionEventStatusResponse getCompletionEventStatus(
            @PathVariable UUID harvestId
    ) {
        return eventStatusService.getStatus(harvestId);
    }

    @PostMapping("/{harvestId}/completion-event/republish")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER')")
    public ResponseEntity<HarvestCompletionEventStatusResponse> republishCompletionEvent(
            @PathVariable UUID harvestId
    ) {
        return ResponseEntity.accepted().body(eventRepairService.republish(harvestId));
    }
}
