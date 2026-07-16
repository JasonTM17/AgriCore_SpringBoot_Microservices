package com.agricore.farm.api.controller;

import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.application.service.FarmApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plots")
public class PlotController {

    private final FarmApplicationService farmService;

    public PlotController(FarmApplicationService farmService) {
        this.farmService = farmService;
    }

    @GetMapping("/{plotId}")
    @PreAuthorize("isAuthenticated()")
    public PlotResponse get(@PathVariable UUID plotId) {
        return farmService.getPlot(plotId);
    }

    @PatchMapping("/{plotId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public PlotResponse update(@PathVariable UUID plotId, @Valid @RequestBody UpdatePlotRequest request) {
        return farmService.updatePlot(plotId, request);
    }
}
