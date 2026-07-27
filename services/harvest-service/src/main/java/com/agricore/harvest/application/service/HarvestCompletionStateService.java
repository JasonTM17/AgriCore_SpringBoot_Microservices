package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestBatchRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class HarvestCompletionStateService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestEventOutboxWriter eventWriter;

    public HarvestCompletionStateService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestEventOutboxWriter eventWriter
    ) {
        this.harvestRepository = harvestRepository;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public HarvestBatchResponse complete(
            UUID harvestId,
            CompleteHarvestBatchRequest request,
            UUID authorizedFarmId
    ) {
        HarvestBatchEntity batch = harvestRepository.findByIdForUpdate(harvestId)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
        if (batch.getStatus() == HarvestStatus.COMPLETED) {
            return HarvestResponseMapper.toResponse(batch);
        }
        if (batch.getStatus() != HarvestStatus.IN_PROGRESS) {
            throw new HarvestException("HARVEST_NOT_IN_PROGRESS", "Only an in-progress harvest can be completed", 409);
        }
        if (batch.getFarmId() != null && !batch.getFarmId().equals(authorizedFarmId)) {
            throw new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404);
        }
        HarvestApplicationService.requireValidWeights(request.grossWeightKg(), request.netWeightKg());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        batch.setGrossWeightKg(request.grossWeightKg());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setQualityGrade(request.qualityGrade().trim().toUpperCase());
        batch.setFarmId(authorizedFarmId);
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setHarvestedAt(now);
        if (request.notes() != null) {
            batch.setNotes(request.notes());
        }
        batch.setUpdatedAt(now);
        UUID eventId = eventWriter.harvestCompleted(
                batch,
                request.farmName(),
                request.plotCode(),
                request.productName(),
                request.careSummary()
        );
        batch.setLastOutboxEventId(eventId);
        harvestRepository.saveAndFlush(batch);
        return HarvestResponseMapper.toResponse(batch);
    }
}
