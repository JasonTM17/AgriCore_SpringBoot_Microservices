package com.agricore.harvest.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.farmaccess.FarmResourceAccess;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.client.CropCycleAccessClient;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class HarvestAccessGuard {

    private final FarmAccessClient farmAccessClient;
    private final CropCycleAccessClient cropCycleAccessClient;

    HarvestAccessGuard(
            FarmAccessClient farmAccessClient,
            CropCycleAccessClient cropCycleAccessClient
    ) {
        this.farmAccessClient = farmAccessClient;
        this.cropCycleAccessClient = cropCycleAccessClient;
    }

    UUID requireNewHarvest(UUID cropCycleId, UUID plotId) {
        FarmResourceAccess access = requirePlotAccess(plotId);
        cropCycleAccessClient.requireCycle(cropCycleId, access.farmId(), plotId);
        return access.farmId();
    }

    UUID requireExistingHarvest(HarvestBatchEntity harvest, boolean validateCropCycle) {
        UUID plotId = harvest.getPlotId();
        try {
            FarmResourceAccess access = requirePlotAccess(plotId);
            if (harvest.getFarmId() != null && !harvest.getFarmId().equals(access.farmId())) {
                throw harvestNotFound();
            }
            if (validateCropCycle) {
                cropCycleAccessClient.requireCycle(
                        harvest.getCropCycleId(),
                        access.farmId(),
                        harvest.getPlotId()
                );
            }
            return access.farmId();
        } catch (FarmAccessException ex) {
            if (ex.getHttpStatus() != 404) {
                throw ex;
            }
            throw harvestNotFound();
        }
    }

    private FarmResourceAccess requirePlotAccess(UUID plotId) {
        FarmResourceAccess access = farmAccessClient.requirePlot(plotId);
        if (access == null || access.farmId() == null || !plotId.equals(access.plotId())) {
            throw new HarvestException(
                    "HARVEST_SCOPE_UNAVAILABLE",
                    "Authoritative harvest farm scope is unavailable",
                    503
            );
        }
        return access;
    }

    private static HarvestException harvestNotFound() {
        return new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404);
    }
}
