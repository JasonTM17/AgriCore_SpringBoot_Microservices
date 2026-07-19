package com.agricore.inventory.application.service;

import com.agricore.inventory.api.response.InventoryHarvestProjectionAcknowledgementResponse;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HarvestProjectionAcknowledgementQueryService {

    private final ProcessedEventJpaRepository processedEventRepository;

    public HarvestProjectionAcknowledgementQueryService(ProcessedEventJpaRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(readOnly = true)
    public InventoryHarvestProjectionAcknowledgementResponse getAcknowledgement(UUID eventId) {
        return processedEventRepository.findById(new ProcessedEventEntity.Pk(
                        eventId.toString(),
                        InventoryApplicationService.HARVEST_CONSUMER
                ))
                .map(processedEvent -> new InventoryHarvestProjectionAcknowledgementResponse(
                        eventId,
                        "INVENTORY",
                        InventoryHarvestProjectionAcknowledgementResponse.State.ACKNOWLEDGED,
                        processedEvent.getProcessedAt()
                ))
                .orElseGet(() -> new InventoryHarvestProjectionAcknowledgementResponse(
                        eventId,
                        "INVENTORY",
                        InventoryHarvestProjectionAcknowledgementResponse.State.NOT_ACKNOWLEDGED,
                        null
                ));
    }
}
