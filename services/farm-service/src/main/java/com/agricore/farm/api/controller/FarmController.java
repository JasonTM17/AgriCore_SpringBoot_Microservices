package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.application.service.FarmApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms")
public class FarmController {

    private final FarmApplicationService farmService;

    public FarmController(FarmApplicationService farmService) {
        this.farmService = farmService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER')")
    public ResponseEntity<FarmResponse> create(@Valid @RequestBody CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.createFarm(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<FarmResponse> list(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Sort springSort = parseSort(sort);
        return farmService.listFarms(province, status, PageRequest.of(page, Math.min(size, 100), springSort));
    }

    @GetMapping("/{farmId}")
    @PreAuthorize("isAuthenticated()")
    public FarmResponse get(@PathVariable UUID farmId) {
        return farmService.getFarm(farmId);
    }

    @PatchMapping("/{farmId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER')")
    public FarmResponse update(@PathVariable UUID farmId, @Valid @RequestBody UpdateFarmRequest request) {
        return farmService.updateFarm(farmId, request);
    }

    @PostMapping("/{farmId}/plots")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public ResponseEntity<PlotResponse> createPlot(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreatePlotRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.createPlot(farmId, request));
    }

    @GetMapping("/{farmId}/plots")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<PlotResponse> listPlots(
            @PathVariable UUID farmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return farmService.listPlots(farmId, PageRequest.of(page, Math.min(size, 100), Sort.by("code").ascending()));
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        if (parts.length == 2 && "desc".equalsIgnoreCase(parts[1])) {
            return Sort.by(parts[0]).descending();
        }
        return Sort.by(parts[0]).ascending();
    }
}
