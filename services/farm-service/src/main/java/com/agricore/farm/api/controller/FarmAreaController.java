package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateFarmAreaRequest;
import com.agricore.farm.api.request.UpdateFarmAreaRequest;
import com.agricore.farm.api.response.FarmAreaResponse;
import com.agricore.farm.application.service.FarmAreaApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/areas")
@Validated
public class FarmAreaController {

    private static final String STATUS_PATTERN = "(?i)ACTIVE|INACTIVE|MAINTENANCE";
    private static final String SORT_PATTERN =
            "(code|name|areaInHectares|status|createdAt|updatedAt),(?i:asc|desc)";

    private final FarmAreaApplicationService areaService;

    public FarmAreaController(FarmAreaApplicationService areaService) {
        this.areaService = areaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERMISSION_FARM_WRITE')")
    public FarmAreaResponse create(
            @PathVariable UUID farmId,
            @Valid @RequestBody CreateFarmAreaRequest request
    ) {
        return areaService.create(farmId, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_FARM_READ')")
    public PageResponse<FarmAreaResponse> list(
            @PathVariable UUID farmId,
            @RequestParam(required = false) @Pattern(regexp = STATUS_PATTERN) String status,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "code,asc") @Pattern(regexp = SORT_PATTERN) String sort
    ) {
        return areaService.list(farmId, status, q, PageRequest.of(page, size, parseSort(sort)));
    }

    @GetMapping("/{areaId}")
    @PreAuthorize("hasAuthority('PERMISSION_FARM_READ')")
    public FarmAreaResponse get(@PathVariable UUID farmId, @PathVariable UUID areaId) {
        return areaService.get(farmId, areaId);
    }

    @PatchMapping("/{areaId}")
    @PreAuthorize("hasAuthority('PERMISSION_FARM_WRITE')")
    public FarmAreaResponse update(
            @PathVariable UUID farmId,
            @PathVariable UUID areaId,
            @Valid @RequestBody UpdateFarmAreaRequest request
    ) {
        return areaService.update(farmId, areaId, request);
    }

    @DeleteMapping("/{areaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERMISSION_FARM_WRITE')")
    public void delete(
            @PathVariable UUID farmId,
            @PathVariable UUID areaId,
            @RequestParam @Min(0) long version
    ) {
        areaService.delete(farmId, areaId, version);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
    }
}
