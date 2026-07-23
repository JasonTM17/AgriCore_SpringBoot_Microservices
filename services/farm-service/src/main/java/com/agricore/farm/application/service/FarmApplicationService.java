package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.domain.model.PlotStatus;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
public class FarmApplicationService {

    private final FarmJpaRepository farmRepository;
    private final PlotJpaRepository plotRepository;
    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final FarmAuthorizationService authorizationService;
    private final FarmMembershipApplicationService membershipService;
    private final FarmResourceResolver resourceResolver;

    public FarmApplicationService(
            FarmJpaRepository farmRepository,
            PlotJpaRepository plotRepository,
            OutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper,
            FarmAuthorizationService authorizationService,
            FarmMembershipApplicationService membershipService,
            FarmResourceResolver resourceResolver
    ) {
        this.farmRepository = farmRepository;
        this.plotRepository = plotRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.membershipService = membershipService;
        this.resourceResolver = resourceResolver;
    }

    @Transactional
    public FarmResponse createFarm(CreateFarmRequest request) {
        String code = request.code().trim().toUpperCase();
        if (farmRepository.existsByCodeIgnoreCase(code)) {
            throw new FarmException("FARM_CODE_EXISTS", "Farm code already exists", 409);
        }

        Instant now = Instant.now();
        FarmEntity farm = new FarmEntity();
        farm.setId(UUID.randomUUID());
        farm.setCode(code);
        farm.setName(request.name().trim());
        farm.setAddress(request.address());
        farm.setProvince(request.province());
        farm.setTotalAreaHa(request.totalAreaHa());
        farm.setLatitude(request.latitude());
        farm.setLongitude(request.longitude());
        farm.setStatus(FarmStatus.ACTIVE);
        farm.setCreatedAt(now);
        farm.setUpdatedAt(now);
        farmRepository.save(farm);
        farmRepository.flush();
        membershipService.grantCreator(farm.getId());

        enqueueEvent("Farm", farm.getId().toString(), EventTypes.FARM_CREATED, "agricore.farm.events", farmPayload(farm));
        return toFarmResponse(farm);
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> listFarms(String province, String status, Pageable pageable) {
        String provinceFilter = StringUtils.hasText(province) ? province.strip() : null;
        FarmStatus statusFilter = StringUtils.hasText(status)
                ? FarmStatus.valueOf(status.toUpperCase())
                : null;
        FarmAuthorizationService.CurrentFarmActor actor = authorizationService.currentActor();
        Page<FarmEntity> page = actor.systemAdmin()
                ? farmRepository.search(provinceFilter, statusFilter, pageable)
                : farmRepository.searchAccessible(actor.subject(), provinceFilter, statusFilter, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toFarmResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public FarmResponse getFarm(UUID farmId) {
        return toFarmResponse(requireFarm(farmId));
    }

    @Transactional
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {
        FarmEntity farm = requireFarm(farmId);
        if (request.name() != null) {
            farm.setName(request.name().trim());
        }
        if (request.address() != null) {
            farm.setAddress(request.address());
        }
        if (request.province() != null) {
            farm.setProvince(request.province());
        }
        if (request.totalAreaHa() != null) {
            farm.setTotalAreaHa(request.totalAreaHa());
        }
        if (request.latitude() != null) {
            farm.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            farm.setLongitude(request.longitude());
        }
        if (request.status() != null) {
            farm.setStatus(FarmStatus.valueOf(request.status().toUpperCase()));
        }
        farm.setUpdatedAt(Instant.now());
        farmRepository.save(farm);
        return toFarmResponse(farm);
    }

    @Transactional
    public PlotResponse createPlot(UUID farmId, CreatePlotRequest request) {
        requireFarm(farmId);
        String code = request.code().trim().toUpperCase();
        if (plotRepository.existsByFarmIdAndCodeIgnoreCase(farmId, code)) {
            throw new FarmException("PLOT_CODE_EXISTS", "Plot code already exists in this farm", 409);
        }

        Instant now = Instant.now();
        PlotEntity plot = new PlotEntity();
        plot.setId(UUID.randomUUID());
        plot.setFarmId(farmId);
        plot.setAreaId(request.areaId());
        plot.setCode(code);
        plot.setName(request.name().trim());
        plot.setAreaInHectares(request.areaInHectares());
        plot.setSoilType(request.soilType());
        plot.setStatus(PlotStatus.AVAILABLE);
        plot.setLatitude(request.latitude());
        plot.setLongitude(request.longitude());
        plot.setCreatedAt(now);
        plot.setUpdatedAt(now);
        plotRepository.save(plot);

        enqueueEvent("Plot", plot.getId().toString(), EventTypes.PLOT_CREATED, "agricore.farm.events", plotPayload(plot));
        return toPlotResponse(plot);
    }

    @Transactional(readOnly = true)
    public PlotResponse getPlot(UUID plotId) {
        return toPlotResponse(requirePlot(plotId));
    }

    @Transactional
    public PlotResponse updatePlot(UUID plotId, UpdatePlotRequest request) {
        PlotEntity plot = requirePlot(plotId);
        PlotStatus previous = plot.getStatus();
        if (request.name() != null) {
            plot.setName(request.name().trim());
        }
        if (request.areaInHectares() != null) {
            plot.setAreaInHectares(request.areaInHectares());
        }
        if (request.soilType() != null) {
            plot.setSoilType(request.soilType());
        }
        if (request.status() != null) {
            plot.setStatus(PlotStatus.valueOf(request.status().toUpperCase()));
        }
        if (request.latitude() != null) {
            plot.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            plot.setLongitude(request.longitude());
        }
        plot.setUpdatedAt(Instant.now());
        plotRepository.save(plot);

        if (request.status() != null && previous != plot.getStatus()) {
            ObjectNode payload = plotPayload(plot);
            payload.put("previousStatus", previous.name());
            enqueueEvent("Plot", plot.getId().toString(), EventTypes.PLOT_STATUS_CHANGED, "agricore.farm.events", payload);
        }
        return toPlotResponse(plot);
    }

    private FarmEntity requireFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmException("FARM_NOT_FOUND", "Farm not found", 404));
    }

    private PlotEntity requirePlot(UUID plotId) {
        return resourceResolver.requireAccessiblePlot(plotId);
    }

    private void enqueueEvent(String aggregateType, String aggregateId, String eventType, String topic, ObjectNode payload) {
        try {
            UUID eventId = UUID.randomUUID();
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId.toString());
            envelope.put("eventType", eventType);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("producer", "farm-service");
            envelope.set("payload", payload);
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new FarmException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }

    private ObjectNode farmPayload(FarmEntity farm) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("farmId", farm.getId().toString());
        n.put("code", farm.getCode());
        n.put("name", farm.getName());
        n.put("province", farm.getProvince());
        n.put("status", farm.getStatus().name());
        return n;
    }

    private ObjectNode plotPayload(PlotEntity plot) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("plotId", plot.getId().toString());
        n.put("farmId", plot.getFarmId().toString());
        n.put("code", plot.getCode());
        n.put("name", plot.getName());
        n.put("status", plot.getStatus().name());
        n.put("areaInHectares", plot.getAreaInHectares());
        return n;
    }

    private FarmResponse toFarmResponse(FarmEntity farm) {
        return new FarmResponse(
                farm.getId(), farm.getCode(), farm.getName(), farm.getAddress(), farm.getProvince(),
                farm.getTotalAreaHa(), farm.getLatitude(), farm.getLongitude(), farm.getStatus().name(),
                farm.getCreatedAt(), farm.getUpdatedAt(), farm.getVersion()
        );
    }

    private PlotResponse toPlotResponse(PlotEntity plot) {
        return new PlotResponse(
                plot.getId(), plot.getFarmId(), plot.getAreaId(), plot.getCode(), plot.getName(),
                plot.getAreaInHectares(), plot.getSoilType(), plot.getStatus().name(),
                plot.getLatitude(), plot.getLongitude(), plot.getCreatedAt(), plot.getUpdatedAt(), plot.getVersion()
        );
    }
}
