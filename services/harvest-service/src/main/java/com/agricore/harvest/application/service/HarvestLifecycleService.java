package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestBatchRequest;
import com.agricore.harvest.api.request.StartHarvestRequest;
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
public class HarvestLifecycleService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestEventOutboxWriter eventWriter;
    private final HarvestCompletionStateService completionStateService;
    private final HarvestMetrics metrics;

    public HarvestLifecycleService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestEventOutboxWriter eventWriter,
            HarvestCompletionStateService completionStateService,
            HarvestMetrics metrics
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
        this.completionStateService = completionStateService;
        this.metrics = metrics;
    }

    @Transactional
    public HarvestBatchResponse start(StartHarvestRequest request) {
        return metrics.recordProcessing(() -> startObserved(request));
    }

    private HarvestBatchResponse startObserved(StartHarvestRequest request) {
        accessGuard.requirePlot(request.plotId());
        String code = request.code().trim().toUpperCase();
        if (harvestRepository.existsByCodeIgnoreCase(code)) {
            throw new HarvestException("HARVEST_CODE_EXISTS", "Harvest batch code already exists", 409);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode(code);
        batch.setCropCycleId(request.cropCycleId());
        batch.setPlotId(request.plotId());
        batch.setWarehouseId(request.warehouseId());
        batch.setProductCode(request.productCode().trim().toUpperCase());
        batch.setStatus(HarvestStatus.IN_PROGRESS);
        batch.setStartedAt(now);
        batch.setNotes(request.notes());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        harvestRepository.save(batch);
        eventWriter.harvestBatchCreated(batch);
        eventWriter.harvestStarted(batch);
        return HarvestResponseMapper.toResponse(batch);
    }

    public HarvestBatchResponse complete(UUID harvestId, CompleteHarvestBatchRequest request) {
        return metrics.recordProcessing(() -> completeObserved(harvestId, request));
    }

    private HarvestBatchResponse completeObserved(UUID harvestId, CompleteHarvestBatchRequest request) {
        HarvestBatchEntity snapshot = require(harvestId);
        accessGuard.requireExistingHarvestPlot(snapshot.getPlotId());
        return completionStateService.complete(harvestId, request);
    }

    private HarvestBatchEntity require(UUID harvestId) {
        return harvestRepository.findById(harvestId)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
    }
}
