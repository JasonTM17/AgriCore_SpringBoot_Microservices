package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.PlotStatus;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class PlotQueryService {

    private final PlotJpaRepository plotRepository;
    private final FarmJpaRepository farmRepository;
    private final FarmAuthorizationService authorizationService;

    public PlotQueryService(
            PlotJpaRepository plotRepository,
            FarmJpaRepository farmRepository,
            FarmAuthorizationService authorizationService
    ) {
        this.plotRepository = plotRepository;
        this.farmRepository = farmRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlotResponse> list(
            UUID farmId,
            String status,
            UUID areaId,
            String query,
            Pageable pageable
    ) {
        authorizationService.requireAccess(farmId);
        if (!farmRepository.existsById(farmId)) {
            throw new FarmException("FARM_NOT_FOUND", "Farm not found", 404);
        }
        PlotStatus statusFilter = StringUtils.hasText(status)
                ? PlotStatus.valueOf(status.toUpperCase())
                : null;
        String queryFilter = StringUtils.hasText(query) ? query.strip() : null;
        Page<PlotEntity> page = plotRepository.searchByFarm(
                farmId,
                statusFilter,
                areaId,
                queryFilter,
                pageable
        );
        return PageResponse.of(
                page.getContent().stream().map(PlotQueryService::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private static PlotResponse toResponse(PlotEntity plot) {
        return new PlotResponse(
                plot.getId(),
                plot.getFarmId(),
                plot.getAreaId(),
                plot.getCode(),
                plot.getName(),
                plot.getAreaInHectares(),
                plot.getSoilType(),
                plot.getStatus().name(),
                plot.getLatitude(),
                plot.getLongitude(),
                plot.getCreatedAt(),
                plot.getUpdatedAt(),
                plot.getVersion()
        );
    }
}
