package com.agricore.work.application.service;

import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.client.InventoryStockClient;
import com.agricore.work.infrastructure.client.InventoryStockClientException;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WorkTaskCompletionService {

    private final WorkTaskCompletionStateService stateService;
    private final InventoryStockClient inventoryStockClient;

    public WorkTaskCompletionService(
            WorkTaskCompletionStateService stateService,
            InventoryStockClient inventoryStockClient
    ) {
        this.stateService = stateService;
        this.inventoryStockClient = inventoryStockClient;
    }

    public WorkTaskEntity complete(UUID taskId, CompleteTaskRequest request, String executedBy) {
        WorkTaskCompletionStateService.CompletionPreparation preparation =
                stateService.prepare(taskId, request.materials());
        if (preparation.alreadyCompleted()) {
            return preparation.task();
        }

        for (UUID usageId : preparation.materialUsageIds()) {
            WorkTaskCompletionStateService.MaterialAttempt attempt = stateService.loadAttempt(usageId);
            if (attempt.alreadyConsumed()) {
                continue;
            }
            consumeMaterial(taskId, preparation.farmId(), usageId, attempt);
        }
        return stateService.finalizeTask(taskId, request.notes(), executedBy);
    }

    private void consumeMaterial(
            UUID taskId,
            UUID farmId,
            UUID usageId,
            WorkTaskCompletionStateService.MaterialAttempt attempt
    ) {
        try {
            InventoryStockClient.StockOutResult result = inventoryStockClient.stockOut(
                    farmId,
                    attempt.inventoryItemId(),
                    attempt.quantity(),
                    attempt.inventoryReferenceId()
            );
            stateService.markConsumed(taskId, usageId, result.unit());
        } catch (InventoryStockClientException exception) {
            if (!stateService.markFailedUnlessConsumed(usageId, exception)) {
                throw new WorkException(
                        "MATERIAL_CONSUMPTION_PENDING",
                        "Material consumption is pending; retry task completion",
                        503
                );
            }
        }
    }
}
