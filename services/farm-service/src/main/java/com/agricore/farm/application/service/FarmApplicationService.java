package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.infrastructure.persistence.EnterpriseJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class FarmApplicationService {

    private final FarmJpaRepository farmRepository;
    private final EnterpriseJpaRepository enterpriseRepository;
    private final FarmAuthorizationService authorizationService;
    private final FarmMembershipApplicationService membershipService;
    private final FarmEventOutboxService eventOutboxService;

    public FarmApplicationService(
            FarmJpaRepository farmRepository,
            EnterpriseJpaRepository enterpriseRepository,
            FarmAuthorizationService authorizationService,
            FarmMembershipApplicationService membershipService,
            FarmEventOutboxService eventOutboxService
    ) {
        this.farmRepository = farmRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.authorizationService = authorizationService;
        this.membershipService = membershipService;
        this.eventOutboxService = eventOutboxService;
    }

    @Transactional
    public FarmResponse createFarm(CreateFarmRequest request) {
        if (request.enterpriseId() != null) {
            validateEnterpriseAssignment(request.enterpriseId());
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (farmRepository.existsByCodeIgnoreCase(code)) {
            throw new FarmException("FARM_CODE_EXISTS", "Farm code already exists", 409);
        }

        Instant now = Instant.now();
        FarmEntity farm = new FarmEntity();
        farm.setId(UUID.randomUUID());
        farm.setCode(code);
        farm.setName(request.name().trim());
        farm.setEnterpriseId(request.enterpriseId());
        farm.setAddress(request.address());
        farm.setProvince(request.province());
        farm.setTotalAreaHa(request.totalAreaHa());
        farm.setLatitude(request.latitude());
        farm.setLongitude(request.longitude());
        farm.setStatus(FarmStatus.ACTIVE);
        farm.setCreatedAt(now);
        farm.setUpdatedAt(now);
        farmRepository.saveAndFlush(farm);
        membershipService.grantCreator(farm.getId());
        eventOutboxService.enqueue(
                "Farm",
                farm.getId().toString(),
                EventTypes.FARM_CREATED,
                "agricore.farm.events",
                FarmMapper.eventPayload(farm)
        );
        return FarmMapper.toResponse(farm);
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> listFarms(
            String province,
            String status,
            UUID enterpriseId,
            Pageable pageable
    ) {
        String provinceFilter = escapedFilter(province);
        FarmStatus statusFilter = StringUtils.hasText(status)
                ? FarmStatus.valueOf(status.toUpperCase(Locale.ROOT))
                : null;
        FarmAuthorizationService.CurrentFarmActor actor = authorizationService.currentActor();
        Page<FarmEntity> page = actor.systemAdmin()
                ? farmRepository.search(provinceFilter, statusFilter, enterpriseId, pageable)
                : farmRepository.searchAccessible(
                        actor.subject(),
                        provinceFilter,
                        statusFilter,
                        enterpriseId,
                        pageable
                );
        return PageResponse.of(
                page.getContent().stream().map(FarmMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public FarmResponse getFarm(UUID farmId) {
        return FarmMapper.toResponse(requireFarm(farmId));
    }

    @Transactional
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {
        FarmEntity farm = requireFarm(farmId);
        FarmUpdatePolicy.validate(request);
        if (request.enterpriseIdPresent()) {
            requireSystemAdminForEnterpriseAssignment();
        }
        if (farm.getVersion() != request.version()) {
            throw new FarmException(
                    "FARM_VERSION_CONFLICT",
                    "Farm changed; reload the latest version before retrying",
                    409
            );
        }
        if (request.enterpriseIdPresent() && request.enterpriseId() != null) {
            requireEnterpriseExists(request.enterpriseId());
        }
        FarmUpdatePolicy.apply(farm, request);
        farm.setUpdatedAt(Instant.now());
        farmRepository.saveAndFlush(farm);
        return FarmMapper.toResponse(farm);
    }

    private FarmEntity requireFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmException("FARM_NOT_FOUND", "Farm not found", 404));
    }

    private void validateEnterpriseAssignment(UUID enterpriseId) {
        requireSystemAdminForEnterpriseAssignment();
        requireEnterpriseExists(enterpriseId);
    }

    private void requireEnterpriseExists(UUID enterpriseId) {
        if (!enterpriseRepository.existsById(enterpriseId)) {
            throw new FarmException("ENTERPRISE_NOT_FOUND", "Enterprise not found", 404);
        }
    }

    private void requireSystemAdminForEnterpriseAssignment() {
        if (!authorizationService.currentActor().systemAdmin()) {
            throw new FarmException(
                    "FARM_ENTERPRISE_ADMIN_REQUIRED",
                    "System administrator role is required to change a farm enterprise",
                    403
            );
        }
    }

    private static String escapedFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
