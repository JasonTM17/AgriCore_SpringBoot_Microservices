package com.agricore.farm.application.service;

import com.agricore.farm.api.request.UpdateIrrigationZoneRequest;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.IrrigationMethod;
import com.agricore.farm.domain.model.IrrigationZoneStatus;
import com.agricore.farm.infrastructure.persistence.entity.IrrigationZoneEntity;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class IrrigationZoneUpdatePolicy {

    private IrrigationZoneUpdatePolicy() {
    }

    static void validate(UpdateIrrigationZoneRequest request) {
        if (!hasChanges(request)) {
            throw new FarmException(
                    "IRRIGATION_ZONE_EMPTY_UPDATE",
                    "Provide at least one irrigation zone field to update",
                    400
            );
        }
        requireText(request.namePresent(), request.name(), "name");
        requireValue(request.methodPresent(), request.method(), "method");
        requireValue(request.flowRatePresent(), request.flowRateLitersPerMinute(), "flow rate");
        requireValue(
                request.targetMoisturePresent(),
                request.targetMoisturePercent(),
                "target moisture"
        );
        requireValue(request.statusPresent(), request.status(), "status");
    }

    static void apply(IrrigationZoneEntity zone, UpdateIrrigationZoneRequest request) {
        if (request.namePresent()) { zone.setName(request.name().strip()); }
        if (request.methodPresent()) {
            zone.setMethod(enumValue(IrrigationMethod.class, request.method()));
        }
        if (request.flowRatePresent()) {
            zone.setFlowRateLitersPerMinute(request.flowRateLitersPerMinute());
        }
        if (request.targetMoisturePresent()) {
            zone.setTargetMoisturePercent(request.targetMoisturePercent());
        }
        if (request.statusPresent()) {
            zone.setStatus(enumValue(IrrigationZoneStatus.class, request.status()));
        }
        if (request.notesPresent()) {
            zone.setNotes(StringUtils.hasText(request.notes()) ? request.notes().strip() : null);
        }
    }

    private static boolean hasChanges(UpdateIrrigationZoneRequest request) {
        return request.namePresent()
                || request.methodPresent()
                || request.flowRatePresent()
                || request.targetMoisturePresent()
                || request.statusPresent()
                || request.notesPresent();
    }

    private static void requireValue(boolean present, Object value, String field) {
        if (present && value == null) {
            throw new FarmException(
                    "IRRIGATION_ZONE_FIELD_REQUIRED",
                    "Irrigation zone " + field + " cannot be null",
                    400
            );
        }
    }

    private static void requireText(boolean present, String value, String field) {
        requireValue(present, value, field);
        if (present && !StringUtils.hasText(value)) {
            throw new FarmException(
                    "IRRIGATION_ZONE_FIELD_REQUIRED",
                    "Irrigation zone " + field + " cannot be blank",
                    400
            );
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
