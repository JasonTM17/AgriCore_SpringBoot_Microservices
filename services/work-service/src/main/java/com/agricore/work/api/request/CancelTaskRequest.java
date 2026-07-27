package com.agricore.work.api.request;

import jakarta.validation.constraints.Size;

public record CancelTaskRequest(
        @Size(max = 2000) String notes
) {
}
