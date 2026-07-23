package com.agricore.cropcatalog.api.controller;

import com.agricore.cropcatalog.api.request.ReplaceCropCareProfileRequest;
import com.agricore.cropcatalog.api.response.CropCareProfileResponse;
import com.agricore.cropcatalog.application.service.CropCareProfileManagementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/crops/{cropId}/care-profile")
public class CropCareProfileManagementController {

    private final CropCareProfileManagementService service;

    public CropCareProfileManagementController(CropCareProfileManagementService service) {
        this.service = service;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','AGRONOMIST')")
    public CropCareProfileResponse replace(
            @PathVariable UUID cropId,
            @Valid @RequestBody ReplaceCropCareProfileRequest request,
            Principal principal
    ) {
        return service.replace(cropId, request, principal.getName());
    }
}
