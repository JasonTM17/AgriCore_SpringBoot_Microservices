package com.agricore.harvest.api.controller;

import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.application.service.HarvestApplicationService;
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

    public HarvestController(HarvestApplicationService service) {
        this.service = service;
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
}
