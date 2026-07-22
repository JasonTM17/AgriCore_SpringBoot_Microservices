package com.agricore.work.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.work.api.request.MaterialUsageRequest;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.domain.model.MaterialUsageStatus;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.infrastructure.client.InventoryStockClientException;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkTaskCompletionStateService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final WorkTaskJpaRepository taskRepository;
    private final MaterialUsageJpaRepository materialUsageRepository;
    private final WorkAccessGuard accessGuard;
    private final WorkEventOutboxWriter eventWriter;

    public WorkTaskCompletionStateService(
            WorkTaskJpaRepository taskRepository,
            MaterialUsageJpaRepository materialUsageRepository,
            WorkAccessGuard accessGuard,
            WorkEventOutboxWriter eventWriter
    ) {
        this.taskRepository = taskRepository;
        this.materialUsageRepository = materialUsageRepository;
        this.accessGuard = accessGuard;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public CompletionPreparation prepare(UUID taskId, List<MaterialUsageRequest> requestedMaterials) {
        WorkTaskEntity task = requireForUpdate(taskId);
        accessGuard.requirePlot(task.getPlotId());
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return new CompletionPreparation(task, List.of(), true);
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new WorkException("TASK_CANCELLED", "Cannot complete a cancelled task", 409);
        }

        MaterialUsageRequestMatcher.validateNoDuplicateItems(requestedMaterials);
        List<MaterialUsageEntity> existing = materialUsageRepository
                .findByWorkTaskIdOrderByCreatedAtAsc(taskId);
        if (existing.isEmpty()) {
            existing = createMaterialUsages(taskId, requestedMaterials);
        } else {
            MaterialUsageRequestMatcher.validateRetryMatches(existing, requestedMaterials);
        }
        markTaskInProgressWhenUsingMaterials(task, existing);
        return new CompletionPreparation(
                task,
                existing.stream().map(MaterialUsageEntity::getId).toList(),
                false
        );
    }

    @Transactional
    public MaterialAttempt loadAttempt(UUID usageId) {
        MaterialUsageEntity usage = requireUsageForUpdate(usageId);
        return new MaterialAttempt(
                usage.getInventoryItemId(),
                usage.getQuantity(),
                usage.getInventoryReferenceId(),
                usage.getStatus() == MaterialUsageStatus.CONSUMED
        );
    }

    @Transactional
    public void markConsumed(UUID taskId, UUID usageId, String unit) {
        MaterialUsageEntity usage = requireUsageForUpdate(usageId);
        if (usage.getStatus() == MaterialUsageStatus.CONSUMED) {
            return;
        }
        Instant now = Instant.now();
        usage.setUnit(unit);
        usage.setStatus(MaterialUsageStatus.CONSUMED);
        usage.setLastError(null);
        usage.setConsumedAt(now);
        usage.setUpdatedAt(now);
        materialUsageRepository.save(usage);
        WorkTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
        eventWriter.materialConsumed(task, usage);
    }

    @Transactional
    public boolean markFailedUnlessConsumed(UUID usageId, InventoryStockClientException exception) {
        MaterialUsageEntity usage = requireUsageForUpdate(usageId);
        if (usage.getStatus() == MaterialUsageStatus.CONSUMED) {
            return true;
        }
        usage.setStatus(MaterialUsageStatus.FAILED);
        usage.setLastError(truncate(exception.getMessage()));
        usage.setUpdatedAt(Instant.now());
        materialUsageRepository.save(usage);
        return false;
    }

    @Transactional
    public WorkTaskEntity finalizeTask(UUID taskId, String notes) {
        WorkTaskEntity task = requireForUpdate(taskId);
        accessGuard.requirePlot(task.getPlotId());
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return task;
        }
        boolean hasUnconsumedMaterial = materialUsageRepository
                .findByWorkTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .anyMatch(usage -> usage.getStatus() != MaterialUsageStatus.CONSUMED);
        if (hasUnconsumedMaterial) {
            throw new WorkException(
                    "MATERIAL_CONSUMPTION_PENDING",
                    "Material consumption is pending; retry task completion",
                    503
            );
        }

        Instant now = Instant.now();
        if (task.getActualStart() == null) {
            task.setActualStart(now);
        }
        task.setActualEnd(now);
        task.setStatus(TaskStatus.COMPLETED);
        if (notes != null) {
            task.setNotes(notes);
        }
        task.setUpdatedAt(now);
        task = taskRepository.saveAndFlush(task);
        eventWriter.workTask(EventTypes.WORK_TASK_COMPLETED, task);
        return task;
    }

    private List<MaterialUsageEntity> createMaterialUsages(
            UUID taskId,
            List<MaterialUsageRequest> materials
    ) {
        Instant now = Instant.now();
        List<MaterialUsageEntity> usages = materials.stream().map(request -> {
            UUID usageId = UUID.randomUUID();
            MaterialUsageEntity usage = new MaterialUsageEntity();
            usage.setId(usageId);
            usage.setWorkTaskId(taskId);
            usage.setInventoryItemId(request.inventoryItemId());
            usage.setQuantity(request.quantity());
            usage.setStatus(MaterialUsageStatus.PENDING);
            usage.setInventoryReferenceId("material-" + usageId);
            usage.setCreatedAt(now);
            usage.setUpdatedAt(now);
            return usage;
        }).toList();
        return materialUsageRepository.saveAll(usages);
    }

    private void markTaskInProgressWhenUsingMaterials(
            WorkTaskEntity task,
            List<MaterialUsageEntity> materialUsages
    ) {
        if (materialUsages.isEmpty() || task.getStatus() == TaskStatus.IN_PROGRESS) {
            return;
        }
        Instant now = Instant.now();
        if (task.getActualStart() == null) {
            task.setActualStart(now);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(now);
        taskRepository.saveAndFlush(task);
    }

    private WorkTaskEntity requireForUpdate(UUID taskId) {
        return taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new WorkException("TASK_NOT_FOUND", "Work task not found", 404));
    }

    private MaterialUsageEntity requireUsageForUpdate(UUID usageId) {
        return materialUsageRepository.findByIdForUpdate(usageId)
                .orElseThrow(() -> new WorkException("MATERIAL_USAGE_NOT_FOUND", "Material usage not found", 500));
    }

    private static String truncate(String message) {
        String safeMessage = message == null || message.isBlank() ? "Inventory stock-out failed" : message;
        return safeMessage.substring(0, Math.min(safeMessage.length(), MAX_ERROR_LENGTH));
    }

    record CompletionPreparation(
            WorkTaskEntity task,
            List<UUID> materialUsageIds,
            boolean alreadyCompleted
    ) {
    }

    record MaterialAttempt(
            UUID inventoryItemId,
            BigDecimal quantity,
            String inventoryReferenceId,
            boolean alreadyConsumed
    ) {
    }
}
