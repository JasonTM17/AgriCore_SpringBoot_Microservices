package com.agricore.work.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignTaskRequest(@NotNull UUID assignedEmployeeId) {
}
