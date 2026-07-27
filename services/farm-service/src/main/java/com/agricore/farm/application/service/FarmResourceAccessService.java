package com.agricore.farm.application.service;

import com.agricore.farm.api.response.FarmResourceAccessResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FarmResourceAccessService {

    private final FarmJpaRepository farmRepository;
    private final FarmAuthorizationService authorizationService;
    private final FarmResourceResolver resourceResolver;

    public FarmResourceAccessService(
            FarmJpaRepository farmRepository,
            FarmAuthorizationService authorizationService,
            FarmResourceResolver resourceResolver
    ) {
        this.farmRepository = farmRepository;
        this.authorizationService = authorizationService;
        this.resourceResolver = resourceResolver;
    }

    @Transactional(readOnly = true)
    public FarmResourceAccessResponse resolveFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        if (!farmRepository.existsById(farmId)) {
            throw new FarmException("FARM_NOT_FOUND", "Farm not found", 404);
        }
        return new FarmResourceAccessResponse(farmId, null);
    }

    @Transactional(readOnly = true)
    public FarmResourceAccessResponse resolvePlot(UUID plotId) {
        PlotEntity plot = requirePlot(plotId);
        return new FarmResourceAccessResponse(plot.getFarmId(), plot.getId());
    }

    @Transactional(readOnly = true)
    public FarmResourceAccessResponse resolveFarmPlot(UUID farmId, UUID plotId) {
        PlotEntity plot = requirePlot(plotId);
        if (!farmId.equals(plot.getFarmId())) {
            throw new FarmException("PLOT_NOT_FOUND", "Plot not found", 404);
        }
        return new FarmResourceAccessResponse(farmId, plotId);
    }

    private PlotEntity requirePlot(UUID plotId) {
        return resourceResolver.requireAccessiblePlot(plotId);
    }
}
