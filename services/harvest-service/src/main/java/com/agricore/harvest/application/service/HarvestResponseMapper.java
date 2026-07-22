package com.agricore.harvest.application.service;

import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;

final class HarvestResponseMapper {

    private HarvestResponseMapper() {
    }

    static HarvestBatchResponse toResponse(HarvestBatchEntity batch) {
        return new HarvestBatchResponse(
                batch.getId(),
                batch.getCode(),
                batch.getCropCycleId(),
                batch.getPlotId(),
                batch.getWarehouseId(),
                batch.getProductCode(),
                batch.getGrossWeightKg(),
                batch.getNetWeightKg(),
                batch.getQualityGrade(),
                batch.getStatus().name(),
                batch.getStartedAt(),
                batch.getHarvestedAt(),
                batch.getNotes(),
                batch.getLastOutboxEventId(),
                batch.getCreatedAt(),
                batch.getVersion()
        );
    }
}
