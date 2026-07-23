package com.agricore.cropcatalog.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcatalog.api.response.CropResponse;
import com.agricore.cropcatalog.application.service.CropCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crops")
@Validated
public class CropController {

    private final CropCatalogService cropCatalogService;

    public CropController(CropCatalogService cropCatalogService) {
        this.cropCatalogService = cropCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CATALOG_READ')")
    public PageResponse<CropResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        return cropCatalogService.list(category, q, PageRequest.of(page, size, sort));
    }

    @GetMapping("/{cropId}")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CATALOG_READ')")
    public CropResponse get(@PathVariable UUID cropId) {
        return cropCatalogService.get(cropId);
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CATALOG_READ')")
    public CropResponse getByCode(@PathVariable String code) {
        return cropCatalogService.getByCode(code);
    }
}
