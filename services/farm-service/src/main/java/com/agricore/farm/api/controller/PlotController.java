package com.agricore.farm.api.controller;

import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.application.service.PlotApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plots")
public class PlotController {

    private final PlotApplicationService plotService;

    public PlotController(PlotApplicationService plotService) {
        this.plotService = plotService;
    }

    @GetMapping("/{plotId}")
    @PreAuthorize("isAuthenticated()")
    public PlotResponse get(@PathVariable UUID plotId) {
        return plotService.get(plotId);
    }

    @PatchMapping("/{plotId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST')")
    public PlotResponse update(@PathVariable UUID plotId, @Valid @RequestBody UpdatePlotRequest request) {
        return plotService.update(plotId, request);
    }
}
