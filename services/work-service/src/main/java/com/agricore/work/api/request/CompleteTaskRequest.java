package com.agricore.work.api.request;

import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(@Size(max = 2000) String notes) {
}
