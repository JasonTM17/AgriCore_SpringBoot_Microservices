package com.agricore.farm.application.service;

import com.agricore.farm.api.request.UpdateEnterpriseRequest;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.EnterpriseStatus;
import com.agricore.farm.infrastructure.persistence.entity.EnterpriseEntity;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class EnterpriseUpdatePolicy {

    private EnterpriseUpdatePolicy() {
    }

    static void validate(UpdateEnterpriseRequest request) {
        if (!hasChanges(request)) {
            throw new FarmException(
                    "ENTERPRISE_EMPTY_UPDATE",
                    "Provide at least one enterprise field to update",
                    400
            );
        }
        if (request.namePresent() && !StringUtils.hasText(request.name())) {
            throw fieldRequired("name");
        }
        if (request.statusPresent() && !StringUtils.hasText(request.status())) {
            throw fieldRequired("status");
        }
    }

    static void apply(EnterpriseEntity enterprise, UpdateEnterpriseRequest request) {
        if (request.namePresent()) {
            enterprise.setName(request.name().strip());
        }
        if (request.legalNamePresent()) {
            enterprise.setLegalName(trimToNull(request.legalName()));
        }
        if (request.taxCodePresent()) {
            enterprise.setTaxCode(normalizeTaxCode(request.taxCode()));
        }
        if (request.addressPresent()) {
            enterprise.setAddress(trimToNull(request.address()));
        }
        if (request.provincePresent()) {
            enterprise.setProvince(trimToNull(request.province()));
        }
        if (request.statusPresent()) {
            enterprise.setStatus(EnterpriseStatus.valueOf(
                    request.status().toUpperCase(Locale.ROOT)
            ));
        }
    }

    static String normalizeTaxCode(String value) {
        return StringUtils.hasText(value) ? value.strip().toUpperCase(Locale.ROOT) : null;
    }

    private static boolean hasChanges(UpdateEnterpriseRequest request) {
        return request.namePresent()
                || request.legalNamePresent()
                || request.taxCodePresent()
                || request.addressPresent()
                || request.provincePresent()
                || request.statusPresent();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static FarmException fieldRequired(String field) {
        return new FarmException(
                "ENTERPRISE_FIELD_REQUIRED",
                "Enterprise " + field + " cannot be null or blank",
                400
        );
    }
}
