package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.response.HarvestBatchResponse;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class HarvestApplicationService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestAccessGuard accessGuard;
    private final HarvestEventOutboxWriter eventWriter;
    private final HarvestMetrics metrics;

    public HarvestApplicationService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestAccessGuard accessGuard,
            HarvestEventOutboxWriter eventWriter,
            HarvestMetrics metrics
    ) {
        this.harvestRepository = harvestRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
        this.metrics = metrics;
    }

    /**
     * Backward-compatible one-step completion endpoint. New clients should use
     * the start and complete lifecycle endpoints managed by HarvestLifecycleService.
     */
    @Transactional
    public HarvestBatchResponse completeHarvest(CompleteHarvestRequest request) {
        return metrics.recordProcessing(() -> completeHarvestObserved(request));
    }

    private HarvestBatchResponse completeHarvestObserved(CompleteHarvestRequest request) {
        accessGuard.requirePlot(request.plotId());
        String code = request.code().trim().toUpperCase();
        requireUniqueCode(code);
        requireValidWeights(request.grossWeightKg(), request.netWeightKg());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode(code);
        batch.setCropCycleId(request.cropCycleId());
        batch.setPlotId(request.plotId());
        batch.setWarehouseId(request.warehouseId());
        batch.setProductCode(request.productCode().trim().toUpperCase());
        batch.setGrossWeightKg(request.grossWeightKg());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setQualityGrade(request.qualityGrade().trim().toUpperCase());
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setStartedAt(now);
        batch.setHarvestedAt(now);
        batch.setNotes(request.notes());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        harvestRepository.save(batch);

        UUID eventId = eventWriter.harvestCompleted(
                batch,
                request.farmName(),
                request.plotCode(),
                request.productName(),
                request.careSummary()
        );
        batch.setLastOutboxEventId(eventId);
        harvestRepository.save(batch);
        return HarvestResponseMapper.toResponse(batch);
    }

    @Transactional(readOnly = true)
    public HarvestBatchResponse get(UUID id) {
        HarvestBatchEntity batch = harvestRepository.findById(id)
                .orElseThrow(() -> new HarvestException("HARVEST_NOT_FOUND", "Harvest batch not found", 404));
        accessGuard.requireExistingHarvestPlot(batch.getPlotId());
        return HarvestResponseMapper.toResponse(batch);
    }

    private void requireUniqueCode(String code) {
        if (harvestRepository.existsByCodeIgnoreCase(code)) {
            throw new HarvestException("HARVEST_CODE_EXISTS", "Harvest batch code already exists", 409);
        }
    }

    static void requireValidWeights(BigDecimal grossWeightKg, BigDecimal netWeightKg) {
        if (netWeightKg.compareTo(grossWeightKg) > 0) {
            throw new HarvestException("INVALID_WEIGHT", "netWeightKg cannot exceed grossWeightKg", 400);
        }
    }
}
