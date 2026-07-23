package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateFarmAreaRequest;
import com.agricore.farm.api.request.UpdateFarmAreaRequest;
import com.agricore.farm.api.response.FarmAreaResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.FarmAreaStatus;
import com.agricore.farm.infrastructure.persistence.FarmAreaJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.FarmAreaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class FarmAreaApplicationService {

    private final FarmAreaJpaRepository areaRepository;
    private final FarmJpaRepository farmRepository;
    private final PlotJpaRepository plotRepository;
    private final FarmAuthorizationService authorizationService;

    public FarmAreaApplicationService(
            FarmAreaJpaRepository areaRepository,
            FarmJpaRepository farmRepository,
            PlotJpaRepository plotRepository,
            FarmAuthorizationService authorizationService
    ) {
        this.areaRepository = areaRepository;
        this.farmRepository = farmRepository;
        this.plotRepository = plotRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public FarmAreaResponse create(UUID farmId, CreateFarmAreaRequest request) {
        requireFarm(farmId);
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (areaRepository.existsByFarmIdAndCodeIgnoreCase(farmId, code)) {
            throw new FarmException("FARM_AREA_CODE_EXISTS", "Farm area code already exists in this farm", 409);
        }

        String actor = authorizationService.currentActor().subject();
        Instant now = Instant.now();
        FarmAreaEntity area = new FarmAreaEntity();
        area.setFarmId(farmId);
        area.setId(UUID.randomUUID());
        area.setCode(code);
        area.setName(request.name().strip());
        area.setAreaInHectares(request.areaInHectares());
        area.setDescription(trimToNull(request.description()));
        area.setStatus(FarmAreaStatus.ACTIVE);
        area.setCreatedAt(now);
        area.setUpdatedAt(now);
        area.setCreatedBy(actor);
        area.setUpdatedBy(actor);
        areaRepository.saveAndFlush(area);
        return toResponse(area);
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmAreaResponse> list(
            UUID farmId,
            String status,
            String query,
            Pageable pageable
    ) {
        requireFarm(farmId);
        FarmAreaStatus statusFilter = StringUtils.hasText(status)
                ? FarmAreaStatus.valueOf(status.toUpperCase(Locale.ROOT))
                : null;
        String queryFilter = StringUtils.hasText(query) ? query.strip() : null;
        Page<FarmAreaEntity> page = areaRepository.searchByFarm(farmId, statusFilter, queryFilter, pageable);
        return PageResponse.of(
                page.getContent().stream().map(FarmAreaApplicationService::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public FarmAreaResponse get(UUID farmId, UUID areaId) {
        return toResponse(requireArea(farmId, areaId));
    }

    @Transactional
    public FarmAreaResponse update(UUID farmId, UUID areaId, UpdateFarmAreaRequest request) {
        FarmAreaEntity area = requireArea(farmId, areaId);
        if (area.getVersion() != request.version()) {
            throw new FarmException(
                    "FARM_AREA_VERSION_CONFLICT",
                    "Farm area changed; reload the latest version before retrying",
                    409
            );
        }
        if (request.name() != null) {
            area.setName(request.name().strip());
        }
        if (request.areaInHectares() != null) {
            area.setAreaInHectares(request.areaInHectares());
        }
        if (request.description() != null) {
            area.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            area.setStatus(FarmAreaStatus.valueOf(request.status().toUpperCase(Locale.ROOT)));
        }
        area.setUpdatedAt(Instant.now());
        area.setUpdatedBy(authorizationService.currentActor().subject());
        areaRepository.saveAndFlush(area);
        return toResponse(area);
    }

    @Transactional
    public void delete(UUID farmId, UUID areaId, long version) {
        FarmAreaEntity area = requireArea(farmId, areaId);
        if (area.getVersion() != version) {
            throw new FarmException(
                    "FARM_AREA_VERSION_CONFLICT",
                    "Farm area changed; reload the latest version before retrying",
                    409
            );
        }
        if (plotRepository.existsByFarmIdAndAreaId(farmId, areaId)) {
            throw new FarmException(
                    "FARM_AREA_IN_USE",
                    "Farm area cannot be deleted while plots are assigned to it",
                    409
            );
        }
        areaRepository.delete(area);
        areaRepository.flush();
    }

    private FarmAreaEntity requireArea(UUID farmId, UUID areaId) {
        requireFarm(farmId);
        return areaRepository.findByFarmIdAndId(farmId, areaId)
                .orElseThrow(() -> new FarmException("FARM_AREA_NOT_FOUND", "Farm area not found", 404));
    }

    private void requireFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        if (!farmRepository.existsById(farmId)) {
            throw new FarmException("FARM_NOT_FOUND", "Farm not found", 404);
        }
    }

    private static FarmAreaResponse toResponse(FarmAreaEntity area) {
        return new FarmAreaResponse(
                area.getId(),
                area.getFarmId(),
                area.getCode(),
                area.getName(),
                area.getAreaInHectares(),
                area.getDescription(),
                area.getStatus().name(),
                area.getCreatedAt(),
                area.getUpdatedAt(),
                area.getCreatedBy(),
                area.getUpdatedBy(),
                area.getVersion()
        );
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
