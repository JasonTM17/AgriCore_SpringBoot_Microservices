package com.agricore.harvest.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.harvest.domain.exception.HarvestException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class HarvestAccessGuard {

    private final FarmAccessClient farmAccessClient;

    HarvestAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requirePlot(UUID plotId) {
        farmAccessClient.requirePlot(plotId);
    }

    void requireExistingHarvestPlot(UUID plotId) {
        try {
            requirePlot(plotId);
        } catch (FarmAccessException ex) {
            if (ex.getHttpStatus() != 404) {
                throw ex;
            }
            throw new HarvestException(
                    "HARVEST_NOT_FOUND",
                    "Harvest batch not found",
                    404
            );
        }
    }
}
