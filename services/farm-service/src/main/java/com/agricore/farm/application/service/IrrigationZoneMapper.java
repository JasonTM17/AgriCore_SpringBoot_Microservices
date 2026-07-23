package com.agricore.farm.application.service;

import com.agricore.farm.api.response.IrrigationZoneResponse;
import com.agricore.farm.infrastructure.persistence.entity.IrrigationZoneEntity;

final class IrrigationZoneMapper {

    private IrrigationZoneMapper() {
    }

    static IrrigationZoneResponse toResponse(IrrigationZoneEntity zone) {
        return new IrrigationZoneResponse(
                zone.getId(),
                zone.getFarmId(),
                zone.getPlotId(),
                zone.getCode(),
                zone.getName(),
                zone.getMethod().name(),
                zone.getFlowRateLitersPerMinute(),
                zone.getTargetMoisturePercent(),
                zone.getStatus().name(),
                zone.getNotes(),
                zone.getCreatedAt(),
                zone.getUpdatedAt(),
                zone.getCreatedBy(),
                zone.getUpdatedBy(),
                zone.getVersion()
        );
    }
}
