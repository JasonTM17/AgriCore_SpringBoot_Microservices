package com.agricore.harvest;

import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;

import java.util.UUID;

final class HarvestCompletionEventTestEnvelope {

    private HarvestCompletionEventTestEnvelope() {
    }

    static String valid(UUID eventId, HarvestBatchEntity harvest) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"HarvestCompleted.v1",
                  "eventVersion":1,
                  "occurredAt":"%s",
                  "producer":"harvest-service",
                  "payload":{
                    "harvestId":"%s",
                    "harvestBatchId":"%s",
                    "cropCycleId":"%s",
                    "plotId":"%s",
                    "warehouseId":"%s",
                    "productCode":"%s",
                    "grossWeightKg":%s,
                    "netWeightKg":%s,
                    "qualityGrade":"%s",
                    "harvestDate":"%s",
                    "productName":"%s"
                  }
                }
                """.formatted(
                eventId,
                harvest.getHarvestedAt(),
                harvest.getId(),
                harvest.getId(),
                harvest.getCropCycleId(),
                harvest.getPlotId(),
                harvest.getWarehouseId(),
                harvest.getProductCode(),
                harvest.getGrossWeightKg().toPlainString(),
                harvest.getNetWeightKg().toPlainString(),
                harvest.getQualityGrade(),
                harvest.getHarvestedAt().toString().substring(0, 10),
                harvest.getProductCode()
        );
    }
}
