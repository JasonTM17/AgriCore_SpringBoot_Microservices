package com.agricore.farm.application.service;

import com.agricore.farm.api.response.EnterpriseResponse;
import com.agricore.farm.infrastructure.persistence.entity.EnterpriseEntity;

final class EnterpriseMapper {

    private EnterpriseMapper() {
    }

    static EnterpriseResponse toResponse(EnterpriseEntity enterprise) {
        return new EnterpriseResponse(
                enterprise.getId(),
                enterprise.getCode(),
                enterprise.getName(),
                enterprise.getLegalName(),
                enterprise.getTaxCode(),
                enterprise.getAddress(),
                enterprise.getProvince(),
                enterprise.getStatus().name(),
                enterprise.getCreatedAt(),
                enterprise.getUpdatedAt(),
                enterprise.getCreatedBy(),
                enterprise.getUpdatedBy(),
                enterprise.getVersion()
        );
    }
}
