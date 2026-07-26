package com.agricore.harvest.application.service;

import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HarvestCompletionEventStatusService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestCompletionEventResolver eventResolver;

    public HarvestCompletionEventStatusService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestCompletionEventResolver eventResolver
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.eventResolver = eventResolver;
    }

    @Transactional(readOnly = true)
    public HarvestCompletionEventStatusResponse getStatus(UUID harvestId) {
        HarvestBatchEntity harvest = harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestException(
                        "HARVEST_NOT_FOUND",
                        "Harvest batch not found",
                        404
                ));
        accessGuard.requireExistingHarvest(harvest, false);

        UUID eventId = harvest.getLastOutboxEventId();
        if (eventId == null) {
            return HarvestCompletionEventStatusFactory.unavailable(harvestId);
        }

        OutboxEventEntity outboxEvent = eventResolver.find(harvest, eventId);
        return HarvestCompletionEventStatusFactory.available(
                harvestId,
                eventId,
                outboxEvent
        );
    }
}
