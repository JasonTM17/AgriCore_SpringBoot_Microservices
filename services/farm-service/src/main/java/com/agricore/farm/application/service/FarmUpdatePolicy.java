package com.agricore.farm.application.service;

import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class FarmUpdatePolicy {

    private FarmUpdatePolicy() {
    }

    static void validate(UpdateFarmRequest request) {
        if (!hasChanges(request)) {
            throw new FarmException(
                    "FARM_EMPTY_UPDATE",
                    "Provide at least one farm field to update",
                    400
            );
        }
        if (request.namePresent() && !StringUtils.hasText(request.name())) {
            throw required("name");
        }
        if (request.statusPresent() && !StringUtils.hasText(request.status())) {
            throw required("status");
        }
    }

    static void apply(FarmEntity farm, UpdateFarmRequest request) {
        if (request.namePresent()) {
            farm.setName(request.name().strip());
        }
        if (request.addressPresent()) {
            farm.setAddress(trimToNull(request.address()));
        }
        if (request.provincePresent()) {
            farm.setProvince(trimToNull(request.province()));
        }
        if (request.totalAreaHaPresent()) {
            farm.setTotalAreaHa(request.totalAreaHa());
        }
        if (request.latitudePresent()) {
            farm.setLatitude(request.latitude());
        }
        if (request.longitudePresent()) {
            farm.setLongitude(request.longitude());
        }
        if (request.statusPresent()) {
            farm.setStatus(FarmStatus.valueOf(request.status().toUpperCase(Locale.ROOT)));
        }
        if (request.enterpriseIdPresent()) {
            farm.setEnterpriseId(request.enterpriseId());
        }
    }

    private static boolean hasChanges(UpdateFarmRequest request) {
        return request.namePresent()
                || request.addressPresent()
                || request.provincePresent()
                || request.totalAreaHaPresent()
                || request.latitudePresent()
                || request.longitudePresent()
                || request.statusPresent()
                || request.enterpriseIdPresent();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static FarmException required(String field) {
        return new FarmException(
                "FARM_FIELD_REQUIRED",
                "Farm " + field + " cannot be null or blank",
                400
        );
    }
}
