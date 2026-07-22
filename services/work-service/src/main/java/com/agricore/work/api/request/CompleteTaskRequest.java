package com.agricore.work.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteTaskRequest(
        @Size(max = 2000) String notes,
        @Valid @Size(max = 50) List<MaterialUsageRequest> materials
) {

    public CompleteTaskRequest {
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public CompleteTaskRequest(String notes) {
        this(notes, List.of());
    }
}
