package com.agricore.farm.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.PlotStatus;
import com.agricore.farm.infrastructure.persistence.FarmAreaJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlotApplicationService {

    private final FarmJpaRepository farmRepository;
    private final FarmAreaJpaRepository areaRepository;
    private final PlotJpaRepository plotRepository;
    private final FarmAuthorizationService authorizationService;
    private final FarmResourceResolver resourceResolver;
    private final FarmEventOutboxService eventOutboxService;

    public PlotApplicationService(
            FarmJpaRepository farmRepository,
            FarmAreaJpaRepository areaRepository,
            PlotJpaRepository plotRepository,
            FarmAuthorizationService authorizationService,
            FarmResourceResolver resourceResolver,
            FarmEventOutboxService eventOutboxService
    ) {
        this.farmRepository = farmRepository;
        this.areaRepository = areaRepository;
        this.plotRepository = plotRepository;
        this.authorizationService = authorizationService;
        this.resourceResolver = resourceResolver;
        this.eventOutboxService = eventOutboxService;
    }

    @Transactional
    public PlotResponse create(UUID farmId, CreatePlotRequest request) {
        requireFarm(farmId);
        requireAreaAssignment(farmId, request.areaId());
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (plotRepository.existsByFarmIdAndCodeIgnoreCase(farmId, code)) {
            throw new FarmException("PLOT_CODE_EXISTS", "Plot code already exists in this farm", 409);
        }

        Instant now = Instant.now();
        PlotEntity plot = new PlotEntity();
        plot.setId(UUID.randomUUID());
        plot.setFarmId(farmId);
        plot.setAreaId(request.areaId());
        plot.setCode(code);
        plot.setName(request.name().strip());
        plot.setAreaInHectares(request.areaInHectares());
        plot.setSoilType(request.soilType());
        plot.setStatus(PlotStatus.AVAILABLE);
        plot.setLatitude(request.latitude());
        plot.setLongitude(request.longitude());
        plot.setCreatedAt(now);
        plot.setUpdatedAt(now);
        plotRepository.saveAndFlush(plot);

        eventOutboxService.enqueue(
                "Plot",
                plot.getId().toString(),
                EventTypes.PLOT_CREATED,
                "agricore.farm.events",
                payload(plot)
        );
        return toResponse(plot);
    }

    @Transactional(readOnly = true)
    public PlotResponse get(UUID plotId) {
        return toResponse(resourceResolver.requireAccessiblePlot(plotId));
    }

    @Transactional
    public PlotResponse update(UUID plotId, UpdatePlotRequest request) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        PlotStatus previousStatus = plot.getStatus();
        apply(plot, request);
        plot.setUpdatedAt(Instant.now());
        plotRepository.saveAndFlush(plot);

        if (request.status() != null && previousStatus != plot.getStatus()) {
            ObjectNode payload = payload(plot);
            payload.put("previousStatus", previousStatus.name());
            eventOutboxService.enqueue(
                    "Plot",
                    plot.getId().toString(),
                    EventTypes.PLOT_STATUS_CHANGED,
                    "agricore.farm.events",
                    payload
            );
        }
        return toResponse(plot);
    }

    private void apply(PlotEntity plot, UpdatePlotRequest request) {
        if (request.name() != null) {
            plot.setName(request.name().strip());
        }
        if (request.areaInHectares() != null) {
            plot.setAreaInHectares(request.areaInHectares());
        }
        if (request.soilType() != null) {
            plot.setSoilType(request.soilType());
        }
        if (request.status() != null) {
            plot.setStatus(PlotStatus.valueOf(request.status().toUpperCase(Locale.ROOT)));
        }
        if (request.latitude() != null) {
            plot.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            plot.setLongitude(request.longitude());
        }
        if (request.areaIdPresent()) {
            requireAreaAssignment(plot.getFarmId(), request.areaId());
            plot.setAreaId(request.areaId());
        }
    }

    private void requireFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        if (!farmRepository.existsById(farmId)) {
            throw new FarmException("FARM_NOT_FOUND", "Farm not found", 404);
        }
    }

    private void requireAreaAssignment(UUID farmId, UUID areaId) {
        if (areaId != null && !areaRepository.existsByFarmIdAndId(farmId, areaId)) {
            throw new FarmException("FARM_AREA_NOT_FOUND", "Farm area not found", 404);
        }
    }

    private static ObjectNode payload(PlotEntity plot) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("plotId", plot.getId().toString());
        payload.put("farmId", plot.getFarmId().toString());
        payload.put("code", plot.getCode());
        payload.put("name", plot.getName());
        payload.put("status", plot.getStatus().name());
        payload.put("areaInHectares", plot.getAreaInHectares());
        return payload;
    }

    private static PlotResponse toResponse(PlotEntity plot) {
        return new PlotResponse(
                plot.getId(), plot.getFarmId(), plot.getAreaId(), plot.getCode(), plot.getName(),
                plot.getAreaInHectares(), plot.getSoilType(), plot.getStatus().name(),
                plot.getLatitude(), plot.getLongitude(), plot.getCreatedAt(), plot.getUpdatedAt(), plot.getVersion()
        );
    }
}
