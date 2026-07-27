package com.agricore.work.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateWorkTaskRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID cropCycleId,
        @NotNull UUID plotId,
        @NotBlank String taskType,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotBlank @Size(max = 32) String priority,
        Instant scheduledStart,
        Instant scheduledEnd
) {
}
