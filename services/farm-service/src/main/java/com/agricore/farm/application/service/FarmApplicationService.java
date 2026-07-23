package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.common.event.EventTypes;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
    private final FarmAuthorizationService authorizationService;
    private final FarmMembershipApplicationService membershipService;
    private final FarmEventOutboxService eventOutboxService;

    public FarmApplicationService(
            FarmJpaRepository farmRepository,
            FarmAuthorizationService authorizationService,
            FarmMembershipApplicationService membershipService,
            FarmEventOutboxService eventOutboxService
    ) {
        this.farmRepository = farmRepository;
        this.authorizationService = authorizationService;
        this.membershipService = membershipService;
        this.eventOutboxService = eventOutboxService;
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

        eventOutboxService.enqueue(
                "Farm",
                farm.getId().toString(),
                EventTypes.FARM_CREATED,
                "agricore.farm.events",
                farmPayload(farm)
        );
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

    private FarmEntity requireFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmException("FARM_NOT_FOUND", "Farm not found", 404));
    }

    private static ObjectNode farmPayload(FarmEntity farm) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("farmId", farm.getId().toString());
        n.put("code", farm.getCode());
        n.put("name", farm.getName());
        n.put("province", farm.getProvince());
        n.put("status", farm.getStatus().name());
        return n;
    }

    private FarmResponse toFarmResponse(FarmEntity farm) {
        return new FarmResponse(
                farm.getId(), farm.getCode(), farm.getName(), farm.getAddress(), farm.getProvince(),
                farm.getTotalAreaHa(), farm.getLatitude(), farm.getLongitude(), farm.getStatus().name(),
                farm.getCreatedAt(), farm.getUpdatedAt(), farm.getVersion()
        );
    }

}
