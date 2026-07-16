package com.agricore.cropcatalog.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcatalog.api.response.CropResponse;
import com.agricore.cropcatalog.application.service.CropCatalogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crops")
public class CropController {

    private final CropCatalogService cropCatalogService;

    public CropController(CropCatalogService cropCatalogService) {
        this.cropCatalogService = cropCatalogService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CropResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return cropCatalogService.list(category, q, PageRequest.of(page, Math.min(size, 100), Sort.by("name")));
    }

    @GetMapping("/{cropId}")
    @PreAuthorize("isAuthenticated()")
    public CropResponse get(@PathVariable UUID cropId) {
        return cropCatalogService.get(cropId);
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("isAuthenticated()")
    public CropResponse getByCode(@PathVariable String code) {
        return cropCatalogService.getByCode(code);
    }
}
