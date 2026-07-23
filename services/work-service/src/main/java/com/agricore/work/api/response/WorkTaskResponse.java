package com.agricore.work.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkTaskResponse(
        UUID id,
        String code,
        UUID cropCycleId,
        UUID plotId,
        String taskType,
        String title,
        String description,
        String priority,
        UUID assignedEmployeeId,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant actualStart,
        Instant actualEnd,
        String status,
        String notes,
        Instant createdAt,
        long version,
        List<MaterialUsageResponse> materials,
        List<TaskAttachmentResponse> attachments
) {
}
