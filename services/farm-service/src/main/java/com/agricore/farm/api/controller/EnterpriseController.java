package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateEnterpriseRequest;
import com.agricore.farm.api.request.UpdateEnterpriseRequest;
import com.agricore.farm.api.response.EnterpriseResponse;
import com.agricore.farm.application.service.EnterpriseApplicationService;
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
@RequestMapping("/api/v1/enterprises")
@Validated
public class EnterpriseController {

    private static final String STATUS_PATTERN = "(?i)ACTIVE|INACTIVE";
    private static final String SORT_PATTERN =
            "(code|name|legalName|taxCode|province|status|createdAt|updatedAt),(?i:asc|desc)";

    private final EnterpriseApplicationService enterpriseService;

    public EnterpriseController(EnterpriseApplicationService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_FARM_ADMIN')")
    public ResponseEntity<EnterpriseResponse> create(
            @Valid @RequestBody CreateEnterpriseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enterpriseService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_FARM_ADMIN')")
    public PageResponse<EnterpriseResponse> list(
            @RequestParam(required = false) @Pattern(regexp = STATUS_PATTERN) String status,
            @RequestParam(required = false) @Size(max = 120) String province,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") @Pattern(regexp = SORT_PATTERN) String sort
    ) {
        return enterpriseService.list(
                status,
                province,
                q,
                PageRequest.of(page, size, parseSort(sort))
        );
    }

    @GetMapping("/{enterpriseId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_FARM_ADMIN')")
    public EnterpriseResponse get(@PathVariable UUID enterpriseId) {
        return enterpriseService.get(enterpriseId);
    }

    @PatchMapping("/{enterpriseId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') and hasAuthority('PERMISSION_FARM_ADMIN')")
    public EnterpriseResponse update(
            @PathVariable UUID enterpriseId,
            @Valid @RequestBody UpdateEnterpriseRequest request
    ) {
        return enterpriseService.update(enterpriseId, request);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        Sort requested = "desc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).descending()
                : Sort.by(parts[0]).ascending();
        return requested.and(Sort.by("id").ascending());
    }
}
