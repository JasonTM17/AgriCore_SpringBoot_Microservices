package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.application.service.FarmApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms")
@Validated
public class FarmController {

    private static final String FARM_STATUS_PATTERN = "(?i)ACTIVE|INACTIVE|MAINTENANCE";
    private static final String FARM_SORT_PATTERN =
            "(code|name|province|totalAreaHa|status|createdAt|updatedAt),(?i:asc|desc)";

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
            @RequestParam(required = false) @Size(max = 120) String province,
            @RequestParam(required = false) @Pattern(regexp = FARM_STATUS_PATTERN) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") @Pattern(regexp = FARM_SORT_PATTERN) String sort
    ) {
        Sort springSort = parseSort(sort);
        return farmService.listFarms(province, status, PageRequest.of(page, size, springSort));
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return farmService.listPlots(farmId, PageRequest.of(page, size, Sort.by("code").ascending()));
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        if (parts.length == 2 && "desc".equalsIgnoreCase(parts[1])) {
            return Sort.by(parts[0]).descending();
        }
        return Sort.by(parts[0]).ascending();
    }
}
