package com.agricore.cropcycle.api.response;

import java.time.Instant;
import java.util.UUID;

public record CropCycleStageHistoryResponse(
        UUID id,
        UUID cropCycleId,
        String previousStage,
        String stage,
        String status,
        String notes,
        String changedBy,
        Instant changedAt,
        long cycleVersion
) {
}
