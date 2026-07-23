package com.agricore.farm.api.controller;

import com.agricore.farm.api.response.FarmResourceAccessResponse;
import com.agricore.farm.application.service.FarmResourceAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/api/v1/farm-access")
@PreAuthorize("hasAuthority('PERMISSION_FARM_READ')")
public class FarmResourceAccessController {

    private final FarmResourceAccessService accessService;

    public FarmResourceAccessController(FarmResourceAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/farms/{farmId}")
    public FarmResourceAccessResponse resolveFarm(@PathVariable UUID farmId) {
        return accessService.resolveFarm(farmId);
    }

    @GetMapping("/plots/{plotId}")
    public FarmResourceAccessResponse resolvePlot(@PathVariable UUID plotId) {
        return accessService.resolvePlot(plotId);
    }

    @GetMapping("/farms/{farmId}/plots/{plotId}")
    public FarmResourceAccessResponse resolveFarmPlot(
            @PathVariable UUID farmId,
            @PathVariable UUID plotId
    ) {
        return accessService.resolveFarmPlot(farmId, plotId);
    }
}
