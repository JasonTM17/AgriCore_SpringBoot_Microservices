package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestBatchRequest;
import com.agricore.harvest.api.request.StartHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HarvestLifecycleService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestCreationTransactionService creationTransactions;
    private final HarvestCompletionStateService completionStateService;
    private final HarvestMetrics metrics;

    public HarvestLifecycleService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestCreationTransactionService creationTransactions,
            HarvestCompletionStateService completionStateService,
            HarvestMetrics metrics
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.creationTransactions = creationTransactions;
        this.completionStateService = completionStateService;
        this.metrics = metrics;
    }

    public HarvestBatchResponse start(StartHarvestRequest request) {
        return metrics.recordProcessing(() -> startObserved(request));
    }

    private HarvestBatchResponse startObserved(StartHarvestRequest request) {
        UUID farmId = accessGuard.requireNewHarvest(
                request.cropCycleId(),
                request.plotId(),
                request.warehouseId()
        );
        return creationTransactions.start(request, farmId);
    }

    public HarvestBatchResponse complete(UUID harvestId, CompleteHarvestBatchRequest request) {
        return metrics.recordProcessing(() -> completeObserved(harvestId, request));
    }

    private HarvestBatchResponse completeObserved(UUID harvestId, CompleteHarvestBatchRequest request) {
        HarvestBatchEntity snapshot = require(harvestId);
        UUID farmId = accessGuard.requireExistingHarvest(snapshot, true);
        return completionStateService.complete(harvestId, request, farmId);
    }

    private HarvestBatchEntity require(UUID harvestId) {
        return harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
    }
}
