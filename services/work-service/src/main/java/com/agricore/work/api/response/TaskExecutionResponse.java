package com.agricore.work.api.response;

import java.time.Instant;
import java.util.UUID;

public record TaskExecutionResponse(
        long id,
        UUID workTaskId,
        String action,
        String previousStatus,
        String status,
        String notes,
        String executedBy,
        Instant executedAt,
        long taskVersion
) {
}
