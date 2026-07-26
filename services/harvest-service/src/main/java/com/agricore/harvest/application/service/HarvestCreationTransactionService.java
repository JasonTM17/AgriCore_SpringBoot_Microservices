package com.agricore.harvest.application.service;

import com.agricore.harvest.api.request.CompleteHarvestRequest;
import com.agricore.harvest.api.request.StartHarvestRequest;
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
class HarvestCreationTransactionService {

    private final HarvestBatchJpaRepository harvestRepository;
    private final HarvestEventOutboxWriter eventWriter;

    HarvestCreationTransactionService(
            HarvestBatchJpaRepository harvestRepository,
            HarvestEventOutboxWriter eventWriter
    ) {
        this.harvestRepository = harvestRepository;
        this.eventWriter = eventWriter;
    }

    @Transactional
    HarvestBatchResponse start(StartHarvestRequest request, UUID farmId) {
        String code = normalizeCode(request.code());
        requireUniqueCode(code);
        Instant now = now();
        HarvestBatchEntity batch = baseBatch(
                code,
                request.cropCycleId(),
                farmId,
                request.plotId(),
                request.warehouseId(),
                request.productCode(),
                request.notes(),
                now
        );
        batch.setStatus(HarvestStatus.IN_PROGRESS);
        harvestRepository.save(batch);
        eventWriter.harvestBatchCreated(batch);
        eventWriter.harvestStarted(batch);
        return HarvestResponseMapper.toResponse(batch);
    }

    @Transactional
    HarvestBatchResponse complete(CompleteHarvestRequest request, UUID farmId) {
        String code = normalizeCode(request.code());
        requireUniqueCode(code);
        HarvestApplicationService.requireValidWeights(
                request.grossWeightKg(),
                request.netWeightKg()
        );
        Instant now = now();
        HarvestBatchEntity batch = baseBatch(
                code,
                request.cropCycleId(),
                farmId,
                request.plotId(),
                request.warehouseId(),
                request.productCode(),
                request.notes(),
                now
        );
        batch.setGrossWeightKg(request.grossWeightKg());
        batch.setNetWeightKg(request.netWeightKg());
        batch.setQualityGrade(request.qualityGrade().trim().toUpperCase());
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setHarvestedAt(now);
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

    private HarvestBatchEntity baseBatch(
            String code,
            UUID cropCycleId,
            UUID farmId,
            UUID plotId,
            UUID warehouseId,
            String productCode,
            String notes,
            Instant now
    ) {
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode(code);
        batch.setCropCycleId(cropCycleId);
        batch.setFarmId(farmId);
        batch.setPlotId(plotId);
        batch.setWarehouseId(warehouseId);
        batch.setProductCode(productCode.trim().toUpperCase());
        batch.setStartedAt(now);
        batch.setNotes(notes);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private void requireUniqueCode(String code) {
        if (harvestRepository.existsByCodeIgnoreCase(code)) {
            throw new HarvestException("HARVEST_CODE_EXISTS", "Harvest batch code already exists", 409);
        }
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
