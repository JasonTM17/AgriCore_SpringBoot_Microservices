package com.agricore.cropcycle.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.application.service.CropCycleApplicationService;
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
@RequestMapping("/api/v1/crop-cycles")
@Validated
public class CropCycleController {

    private final CropCycleApplicationService service;

    public CropCycleController(CropCycleApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public ResponseEntity<CropCycleResponse> create(@Valid @RequestBody CreateCropCycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CropCycleResponse> list(
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID plotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.list(farmId, plotId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public CropCycleResponse get(@PathVariable UUID cycleId) {
        return service.get(cycleId);
    }

    @PostMapping("/{cycleId}/stage")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public CropCycleResponse changeStage(
            @PathVariable UUID cycleId,
            @Valid @RequestBody ChangeStageRequest request
    ) {
        return service.changeStage(cycleId, request);
    }
}
