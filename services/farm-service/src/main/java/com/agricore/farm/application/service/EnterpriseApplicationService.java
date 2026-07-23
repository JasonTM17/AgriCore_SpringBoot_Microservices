package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateEnterpriseRequest;
import com.agricore.farm.api.request.UpdateEnterpriseRequest;
import com.agricore.farm.api.response.EnterpriseResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.EnterpriseStatus;
import com.agricore.farm.infrastructure.persistence.EnterpriseJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.EnterpriseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class EnterpriseApplicationService {

    private final EnterpriseJpaRepository enterpriseRepository;
    private final FarmAuthorizationService authorizationService;

    public EnterpriseApplicationService(
            EnterpriseJpaRepository enterpriseRepository,
            FarmAuthorizationService authorizationService
    ) {
        this.enterpriseRepository = enterpriseRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public EnterpriseResponse create(CreateEnterpriseRequest request) {
        FarmAuthorizationService.CurrentFarmActor actor = requireSystemAdmin();
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        String taxCode = EnterpriseUpdatePolicy.normalizeTaxCode(request.taxCode());
        if (enterpriseRepository.existsByCodeIgnoreCase(code)) {
            throw conflict("ENTERPRISE_CODE_EXISTS", "Enterprise code already exists");
        }
        if (taxCode != null && enterpriseRepository.existsByTaxCodeIgnoreCase(taxCode)) {
            throw conflict("ENTERPRISE_TAX_CODE_EXISTS", "Enterprise tax code already exists");
        }

        Instant now = Instant.now();
        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(UUID.randomUUID());
        enterprise.setCode(code);
        enterprise.setName(request.name().strip());
        enterprise.setLegalName(trimToNull(request.legalName()));
        enterprise.setTaxCode(taxCode);
        enterprise.setAddress(trimToNull(request.address()));
        enterprise.setProvince(trimToNull(request.province()));
        enterprise.setStatus(EnterpriseStatus.ACTIVE);
        enterprise.setCreatedAt(now);
        enterprise.setUpdatedAt(now);
        enterprise.setCreatedBy(actor.subject());
        enterprise.setUpdatedBy(actor.subject());
        enterpriseRepository.saveAndFlush(enterprise);
        return EnterpriseMapper.toResponse(enterprise);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnterpriseResponse> list(
            String status,
            String province,
            String query,
            Pageable pageable
    ) {
        requireSystemAdmin();
        EnterpriseStatus statusFilter = StringUtils.hasText(status)
                ? EnterpriseStatus.valueOf(status.toUpperCase(Locale.ROOT))
                : null;
        Page<EnterpriseEntity> page = enterpriseRepository.search(
                statusFilter,
                escapedFilter(province),
                escapedFilter(query),
                pageable
        );
        return PageResponse.of(
                page.getContent().stream().map(EnterpriseMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public EnterpriseResponse get(UUID enterpriseId) {
        requireSystemAdmin();
        return EnterpriseMapper.toResponse(requireEnterprise(enterpriseId));
    }

    @Transactional
    public EnterpriseResponse update(
            UUID enterpriseId,
            UpdateEnterpriseRequest request
    ) {
        FarmAuthorizationService.CurrentFarmActor actor = requireSystemAdmin();
        EnterpriseEntity enterprise = requireEnterprise(enterpriseId);
        EnterpriseUpdatePolicy.validate(request);
        if (enterprise.getVersion() != request.version()) {
            throw conflict(
                    "ENTERPRISE_VERSION_CONFLICT",
                    "Enterprise changed; reload the latest version before retrying"
            );
        }
        String taxCode = EnterpriseUpdatePolicy.normalizeTaxCode(request.taxCode());
        if (request.taxCodePresent()
                && taxCode != null
                && enterpriseRepository.existsByTaxCodeIgnoreCaseAndIdNot(
                        taxCode,
                        enterpriseId
                )) {
            throw conflict(
                    "ENTERPRISE_TAX_CODE_EXISTS",
                    "Enterprise tax code already exists"
            );
        }

        EnterpriseUpdatePolicy.apply(enterprise, request);
        enterprise.setUpdatedAt(Instant.now());
        enterprise.setUpdatedBy(actor.subject());
        enterpriseRepository.saveAndFlush(enterprise);
        return EnterpriseMapper.toResponse(enterprise);
    }

    private FarmAuthorizationService.CurrentFarmActor requireSystemAdmin() {
        FarmAuthorizationService.CurrentFarmActor actor = authorizationService.currentActor();
        if (!actor.systemAdmin()) {
            throw new FarmException(
                    "ENTERPRISE_ADMIN_REQUIRED",
                    "System administrator role is required",
                    403
            );
        }
        return actor;
    }

    private EnterpriseEntity requireEnterprise(UUID enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new FarmException(
                        "ENTERPRISE_NOT_FOUND",
                        "Enterprise not found",
                        404
                ));
    }

    private static String escapedFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static FarmException conflict(String code, String message) {
        return new FarmException(code, message, 409);
    }
}
