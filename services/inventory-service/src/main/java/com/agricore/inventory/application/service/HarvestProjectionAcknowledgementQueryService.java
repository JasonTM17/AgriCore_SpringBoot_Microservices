package com.agricore.inventory.application.service;

import com.agricore.inventory.api.response.InventoryHarvestProjectionAcknowledgementResponse;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HarvestProjectionAcknowledgementQueryService {

    private final ProcessedEventJpaRepository processedEventRepository;

    public HarvestProjectionAcknowledgementQueryService(
            ProcessedEventJpaRepository processedEventRepository
    ) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(readOnly = true)
    public InventoryHarvestProjectionAcknowledgementResponse getAcknowledgement(
            UUID eventId,
            UUID warehouseId,
            UUID farmId
    ) {
        return processedEventRepository.findById(new ProcessedEventEntity.Pk(
                        eventId.toString(),
                        InventoryApplicationService.HARVEST_CONSUMER
                ))
                .map(processedEvent -> acknowledged(eventId, warehouseId, farmId, processedEvent))
                .orElseGet(() -> new InventoryHarvestProjectionAcknowledgementResponse(
                        eventId,
                        "INVENTORY",
                        InventoryHarvestProjectionAcknowledgementResponse.State.NOT_ACKNOWLEDGED,
                        null
                ));
    }

    private InventoryHarvestProjectionAcknowledgementResponse acknowledged(
            UUID eventId,
            UUID warehouseId,
            UUID farmId,
            ProcessedEventEntity processedEvent
    ) {
        if (processedEvent.getFarmId() == null || processedEvent.getWarehouseId() == null) {
            throw new InventoryException(
                    "ACKNOWLEDGEMENT_SCOPE_UNAVAILABLE",
                    "Farm scope is unavailable for this legacy acknowledgement",
                    503
            );
        }
        if (!farmId.equals(processedEvent.getFarmId())
                || !warehouseId.equals(processedEvent.getWarehouseId())) {
            return new InventoryHarvestProjectionAcknowledgementResponse(
                    eventId,
                    "INVENTORY",
                    InventoryHarvestProjectionAcknowledgementResponse.State.NOT_ACKNOWLEDGED,
                    null
            );
        }
        return new InventoryHarvestProjectionAcknowledgementResponse(
                eventId,
                "INVENTORY",
                InventoryHarvestProjectionAcknowledgementResponse.State.ACKNOWLEDGED,
                processedEvent.getProcessedAt()
        );
    }
}
