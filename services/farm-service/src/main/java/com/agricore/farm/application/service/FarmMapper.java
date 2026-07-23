package com.agricore.farm.application.service;

import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class FarmMapper {

    private FarmMapper() {
    }

    static FarmResponse toResponse(FarmEntity farm) {
        return new FarmResponse(
                farm.getId(),
                farm.getCode(),
                farm.getName(),
                farm.getEnterpriseId(),
                farm.getAddress(),
                farm.getProvince(),
                farm.getTotalAreaHa(),
                farm.getLatitude(),
                farm.getLongitude(),
                farm.getStatus().name(),
                farm.getCreatedAt(),
                farm.getUpdatedAt(),
                farm.getVersion()
        );
    }

    static ObjectNode eventPayload(FarmEntity farm) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("farmId", farm.getId().toString());
        payload.put("code", farm.getCode());
        payload.put("name", farm.getName());
        payload.put("province", farm.getProvince());
        payload.put("status", farm.getStatus().name());
        return payload;
    }
}
