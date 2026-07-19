package com.agricore.harvest.application.service;

import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HarvestCompletionEventRepairService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestCompletionEventResolver eventResolver;

    public HarvestCompletionEventRepairService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestCompletionEventResolver eventResolver
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.eventResolver = eventResolver;
    }

    @Transactional
    public HarvestCompletionEventStatusResponse republish(UUID harvestId) {
        HarvestBatchEntity harvest = harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestException(
                        "HARVEST_NOT_FOUND",
                        "Harvest batch not found",
                        404
                ));
        accessGuard.requireExistingHarvestPlot(harvest.getPlotId());

        UUID eventId = harvest.getLastOutboxEventId();
        if (eventId == null) {
            throw new HarvestException(
                    "OUTBOX_EVENT_UNAVAILABLE",
                    "Legacy harvest has no stable completion event to republish",
                    409
            );
        }

        OutboxEventEntity currentEvent = eventResolver.find(harvest, eventId);
        if (currentEvent.getPublishedAt() == null) {
            return HarvestCompletionEventStatusFactory.available(harvestId, eventId, currentEvent);
        }

        OutboxEventEntity event;
        try {
            event = eventResolver.findForUpdate(harvest, eventId);
        } catch (PessimisticLockingFailureException ex) {
            throw new HarvestException(
                    "OUTBOX_EVENT_BUSY",
                    "Harvest completion event is being updated; retry later",
                    503
            );
        }
        event.requeueForRepublish();
        return HarvestCompletionEventStatusFactory.available(harvestId, eventId, event);
    }
}
