package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateSoilProfileRequest;
import com.agricore.farm.api.request.UpdateSoilProfileRequest;
import com.agricore.farm.api.response.SoilProfileResponse;
import com.agricore.farm.application.service.SoilProfileApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plots/{plotId}/soil-profiles")
@Validated
public class SoilProfileController {

    private static final String STATUS_PATTERN = "(?i)ACTIVE|ARCHIVED";
    private static final String SORT_PATTERN =
            "(sampledAt|sampleCode|ph|status|createdAt|updatedAt),(?i:asc|desc)";

    private final SoilProfileApplicationService profileService;

    public SoilProfileController(SoilProfileApplicationService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public ResponseEntity<SoilProfileResponse> create(
            @PathVariable UUID plotId,
            @Valid @RequestBody CreateSoilProfileRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.create(plotId, request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<SoilProfileResponse> list(
            @PathVariable UUID plotId,
            @RequestParam(required = false) @Pattern(regexp = STATUS_PATTERN) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sampledFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sampledTo,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "sampledAt,desc")
            @Pattern(regexp = SORT_PATTERN) String sort
    ) {
        return profileService.list(
                plotId,
                status,
                sampledFrom,
                sampledTo,
                q,
                PageRequest.of(page, size, parseSort(sort))
        );
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("isAuthenticated()")
    public SoilProfileResponse get(
            @PathVariable UUID plotId,
            @PathVariable UUID profileId
    ) {
        return profileService.get(plotId, profileId);
    }

    @PatchMapping("/{profileId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public SoilProfileResponse update(
            @PathVariable UUID plotId,
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateSoilProfileRequest request
    ) {
        return profileService.update(plotId, profileId, request);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        return "desc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).descending()
                : Sort.by(parts[0]).ascending();
    }
}
