package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class HarvestApplicationService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestCreationTransactionService creationTransactions;
    private final HarvestMetrics metrics;

    public HarvestApplicationService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestCreationTransactionService creationTransactions,
            HarvestMetrics metrics
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.creationTransactions = creationTransactions;
        this.metrics = metrics;
    }

    /**
     * Backward-compatible one-step completion endpoint. New clients should use
     * the start and complete lifecycle endpoints managed by HarvestLifecycleService.
     */
    public HarvestBatchResponse completeHarvest(CompleteHarvestRequest request) {
        return metrics.recordProcessing(() -> completeHarvestObserved(request));
    }

    private HarvestBatchResponse completeHarvestObserved(CompleteHarvestRequest request) {
        UUID farmId = accessGuard.requireNewHarvest(request.cropCycleId(), request.plotId());
        return creationTransactions.complete(request, farmId);
    }

    public HarvestBatchResponse get(UUID id) {
        HarvestBatchEntity batch = harvestRepository.findById(id)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
        accessGuard.requireExistingHarvest(batch, false);
        return HarvestResponseMapper.toResponse(batch);
    }

    static void requireValidWeights(BigDecimal grossWeightKg, BigDecimal netWeightKg) {
        if (grossWeightKg == null
                || netWeightKg == null
                || grossWeightKg.signum() <= 0
                || netWeightKg.signum() <= 0
                || netWeightKg.compareTo(grossWeightKg) > 0) {
            throw new HarvestException(
                    "INVALID_WEIGHT",
                    "Weights must be positive and netWeightKg cannot exceed grossWeightKg",
                    400
            );
        }
    }
}
