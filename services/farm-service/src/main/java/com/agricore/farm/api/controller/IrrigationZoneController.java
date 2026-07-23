package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateIrrigationZoneRequest;
import com.agricore.farm.api.request.UpdateIrrigationZoneRequest;
import com.agricore.farm.api.response.IrrigationZoneResponse;
import com.agricore.farm.application.service.IrrigationZoneApplicationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plots/{plotId}/irrigation-zones")
@Validated
public class IrrigationZoneController {

    private static final String METHOD_PATTERN =
            "(?i)DRIP|SPRINKLER|MICRO_SPRINKLER|CENTER_PIVOT|FLOOD|MANUAL";
    private static final String STATUS_PATTERN = "(?i)ACTIVE|MAINTENANCE|INACTIVE";
    private static final String SORT_PATTERN =
            "(code|name|method|flowRateLitersPerMinute|targetMoisturePercent|status|"
                    + "createdAt|updatedAt),(?i:asc|desc)";

    private final IrrigationZoneApplicationService zoneService;

    public IrrigationZoneController(IrrigationZoneApplicationService zoneService) {
        this.zoneService = zoneService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_FARM_WRITE')")
    public ResponseEntity<IrrigationZoneResponse> create(
            @PathVariable UUID plotId,
            @Valid @RequestBody CreateIrrigationZoneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.create(plotId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_FARM_READ')")
    public PageResponse<IrrigationZoneResponse> list(
            @PathVariable UUID plotId,
            @RequestParam(required = false) @Pattern(regexp = STATUS_PATTERN) String status,
            @RequestParam(required = false) @Pattern(regexp = METHOD_PATTERN) String method,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") @Pattern(regexp = SORT_PATTERN) String sort
    ) {
        return zoneService.list(
                plotId,
                status,
                method,
                q,
                PageRequest.of(page, size, parseSort(sort))
        );
    }

    @GetMapping("/{zoneId}")
    @PreAuthorize("hasAuthority('PERMISSION_FARM_READ')")
    public IrrigationZoneResponse get(
            @PathVariable UUID plotId,
            @PathVariable UUID zoneId
    ) {
        return zoneService.get(plotId, zoneId);
    }

    @PatchMapping("/{zoneId}")
    @PreAuthorize("hasAuthority('PERMISSION_FARM_WRITE')")
    public IrrigationZoneResponse update(
            @PathVariable UUID plotId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateIrrigationZoneRequest request
    ) {
        return zoneService.update(plotId, zoneId, request);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        Sort requested = "desc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).descending()
                : Sort.by(parts[0]).ascending();
        return requested.and(Sort.by("id").ascending());
    }
}
