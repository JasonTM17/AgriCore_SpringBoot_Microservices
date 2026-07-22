package com.agricore.work.api.response;

import java.time.Instant;
import java.util.UUID;

public record WorkAssignmentResponse(
        UUID id,
        UUID workTaskId,
        UUID employeeId,
        String assignedBy,
        Instant assignedAt,
        long taskVersion
) {
}
