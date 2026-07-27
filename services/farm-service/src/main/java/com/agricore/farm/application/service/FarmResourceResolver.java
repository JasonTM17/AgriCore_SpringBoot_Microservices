package com.agricore.farm.application.service;

import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FarmResourceResolver {

    private final PlotJpaRepository plotRepository;
    private final FarmAuthorizationService authorizationService;

    public FarmResourceResolver(
            PlotJpaRepository plotRepository,
            FarmAuthorizationService authorizationService
    ) {
        this.plotRepository = plotRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public PlotEntity requireAccessiblePlot(UUID plotId) {
        FarmAuthorizationService.CurrentFarmActor actor = authorizationService.currentActor();
        Optional<PlotEntity> plot = actor.systemAdmin()
                ? plotRepository.findById(plotId)
                : plotRepository.findAccessibleById(plotId, actor.subject());
        return plot.orElseThrow(() -> new FarmException("PLOT_NOT_FOUND", "Plot not found", 404));
    }
}
