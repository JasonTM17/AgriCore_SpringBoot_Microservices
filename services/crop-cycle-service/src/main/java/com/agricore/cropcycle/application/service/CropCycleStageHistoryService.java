package com.agricore.cropcycle.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcycle.api.response.CropCycleStageHistoryResponse;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleStageHistoryJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleStageHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CropCycleStageHistoryService {

    private final CropCycleStageHistoryJpaRepository historyRepository;
    private final CropCycleJpaRepository cycleRepository;
    private final CropCycleAccessGuard accessGuard;

    public CropCycleStageHistoryService(
            CropCycleStageHistoryJpaRepository historyRepository,
            CropCycleJpaRepository cycleRepository,
            CropCycleAccessGuard accessGuard
    ) {
        this.historyRepository = historyRepository;
        this.cycleRepository = cycleRepository;
        this.accessGuard = accessGuard;
    }

    void record(CropCycleEntity cycle, CycleStage previousStage, String changedBy, String notes) {
        CropCycleStageHistoryEntity history = new CropCycleStageHistoryEntity();
        history.setId(UUID.randomUUID());
        history.setCropCycleId(cycle.getId());
        history.setPreviousStage(previousStage == null ? null : previousStage.name());
        history.setStage(cycle.getStage().name());
        history.setStatus(cycle.getStatus().name());
        history.setNotes(notes);
        history.setChangedBy(AuthenticatedActor.requireValid(changedBy));
        history.setChangedAt(Instant.now());
        history.setCycleVersion(cycle.getVersion());
        historyRepository.save(history);
    }

    @Transactional(readOnly = true)
    public PageResponse<CropCycleStageHistoryResponse> list(UUID cropCycleId, Pageable pageable) {
        CropCycleEntity cycle = cycleRepository.findById(cropCycleId)
                .orElseThrow(() -> new CropCycleException("CYCLE_NOT_FOUND", "Crop cycle not found", 404));
        accessGuard.requireFarmPlot(cycle.getFarmId(), cycle.getPlotId());
        Page<CropCycleStageHistoryEntity> page = historyRepository.findByCropCycleId(cropCycleId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private CropCycleStageHistoryResponse toResponse(CropCycleStageHistoryEntity history) {
        return new CropCycleStageHistoryResponse(
                history.getId(),
                history.getCropCycleId(),
                history.getPreviousStage(),
                history.getStage(),
                history.getStatus(),
                history.getNotes(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getCycleVersion()
        );
    }

}
