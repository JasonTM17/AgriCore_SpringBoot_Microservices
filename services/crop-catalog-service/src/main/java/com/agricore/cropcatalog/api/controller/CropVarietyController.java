package com.agricore.cropcatalog.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcatalog.api.response.CropVarietyResponse;
import com.agricore.cropcatalog.application.service.CropVarietyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
public class CropVarietyController {

    private final CropVarietyService varietyService;

    public CropVarietyController(CropVarietyService varietyService) {
        this.varietyService = varietyService;
    }

    @GetMapping("/api/v1/crops/{cropId}/varieties")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CropVarietyResponse> list(
            @PathVariable UUID cropId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        return varietyService.list(cropId, q, PageRequest.of(page, size, sort));
    }

    @GetMapping("/api/v1/crop-varieties/{varietyId}")
    @PreAuthorize("isAuthenticated()")
    public CropVarietyResponse get(@PathVariable UUID varietyId) {
        return varietyService.get(varietyId);
    }
}
