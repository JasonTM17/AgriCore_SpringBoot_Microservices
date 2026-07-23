package com.agricore.cropcatalog.api.controller;

import com.agricore.cropcatalog.api.response.CropCareProfileResponse;
import com.agricore.cropcatalog.application.service.CropCareProfileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crops/{cropId}/care-profile")
public class CropCareProfileController {

    private final CropCareProfileService service;

    public CropCareProfileController(CropCareProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CATALOG_READ')")
    public CropCareProfileResponse get(@PathVariable UUID cropId) {
        return service.get(cropId);
    }
}
