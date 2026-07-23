package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateIrrigationZoneRequest;
import com.agricore.farm.api.request.UpdateIrrigationZoneRequest;
import com.agricore.farm.api.response.IrrigationZoneResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.IrrigationMethod;
import com.agricore.farm.domain.model.IrrigationZoneStatus;
import com.agricore.farm.infrastructure.persistence.IrrigationZoneJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.IrrigationZoneEntity;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class IrrigationZoneApplicationService {

    private final IrrigationZoneJpaRepository zoneRepository;
    private final FarmResourceResolver resourceResolver;
    private final FarmAuthorizationService authorizationService;

    public IrrigationZoneApplicationService(
            IrrigationZoneJpaRepository zoneRepository,
            FarmResourceResolver resourceResolver,
            FarmAuthorizationService authorizationService
    ) {
        this.zoneRepository = zoneRepository;
        this.resourceResolver = resourceResolver;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public IrrigationZoneResponse create(UUID plotId, CreateIrrigationZoneRequest request) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (zoneRepository.existsByFarmIdAndPlotIdAndCodeIgnoreCase(
                plot.getFarmId(),
                plotId,
                code
        )) {
            throw new FarmException(
                    "IRRIGATION_ZONE_CODE_EXISTS",
                    "Irrigation zone code already exists for this plot",
                    409
            );
        }

        String actor = authorizationService.currentActor().subject();
        Instant now = Instant.now();
        IrrigationZoneEntity zone = new IrrigationZoneEntity();
        zone.setId(UUID.randomUUID());
        zone.setFarmId(plot.getFarmId());
        zone.setPlotId(plotId);
        zone.setCode(code);
        zone.setName(request.name().strip());
        zone.setMethod(enumValue(IrrigationMethod.class, request.method()));
        zone.setFlowRateLitersPerMinute(request.flowRateLitersPerMinute());
        zone.setTargetMoisturePercent(request.targetMoisturePercent());
        zone.setStatus(IrrigationZoneStatus.ACTIVE);
        zone.setNotes(trimToNull(request.notes()));
        zone.setCreatedAt(now);
        zone.setUpdatedAt(now);
        zone.setCreatedBy(actor);
        zone.setUpdatedBy(actor);
        zoneRepository.saveAndFlush(zone);
        return IrrigationZoneMapper.toResponse(zone);
    }

    @Transactional(readOnly = true)
    public PageResponse<IrrigationZoneResponse> list(
            UUID plotId,
            String status,
            String method,
            String query,
            Pageable pageable
    ) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        IrrigationZoneStatus statusFilter = StringUtils.hasText(status)
                ? enumValue(IrrigationZoneStatus.class, status)
                : null;
        IrrigationMethod methodFilter = StringUtils.hasText(method)
                ? enumValue(IrrigationMethod.class, method)
                : null;
        String queryFilter = StringUtils.hasText(query) ? escapeLike(query.strip()) : null;
        Page<IrrigationZoneEntity> page = zoneRepository.searchByPlot(
                plot.getFarmId(),
                plotId,
                statusFilter,
                methodFilter,
                queryFilter,
                pageable
        );
        return PageResponse.of(
                page.getContent().stream().map(IrrigationZoneMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public IrrigationZoneResponse get(UUID plotId, UUID zoneId) {
        return IrrigationZoneMapper.toResponse(requireZone(plotId, zoneId));
    }

    @Transactional
    public IrrigationZoneResponse update(
            UUID plotId,
            UUID zoneId,
            UpdateIrrigationZoneRequest request
    ) {
        IrrigationZoneEntity zone = requireZone(plotId, zoneId);
        IrrigationZoneUpdatePolicy.validate(request);
        if (zone.getVersion() != request.version()) {
            throw new FarmException(
                    "IRRIGATION_ZONE_VERSION_CONFLICT",
                    "Irrigation zone changed; reload the latest version before retrying",
                    409
            );
        }
        IrrigationZoneUpdatePolicy.apply(zone, request);
        zone.setUpdatedAt(Instant.now());
        zone.setUpdatedBy(authorizationService.currentActor().subject());
        zoneRepository.saveAndFlush(zone);
        return IrrigationZoneMapper.toResponse(zone);
    }

    private IrrigationZoneEntity requireZone(UUID plotId, UUID zoneId) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        return zoneRepository.findByFarmIdAndPlotIdAndId(plot.getFarmId(), plotId, zoneId)
                .orElseThrow(() -> new FarmException(
                        "IRRIGATION_ZONE_NOT_FOUND",
                        "Irrigation zone not found",
                        404
                ));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
