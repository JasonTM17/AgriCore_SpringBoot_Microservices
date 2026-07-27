package com.agricore.cropcycle.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class CropCycleAccessGuard {

    private final FarmAccessClient farmAccessClient;

    CropCycleAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requireFarmPlot(UUID farmId, UUID plotId) {
        farmAccessClient.requireFarmPlot(farmId, plotId);
    }

    void requireListScope(UUID farmId, UUID plotId) {
        if (farmId != null && plotId != null) {
            farmAccessClient.requireFarmPlot(farmId, plotId);
            return;
        }
        if (plotId != null) {
            farmAccessClient.requirePlot(plotId);
            return;
        }
        if (farmId != null) {
            farmAccessClient.requireFarm(farmId);
            return;
        }
        if (!farmAccessClient.isSystemAdmin()) {
            throw FarmAccessException.scopeRequired();
        }
    }
}
