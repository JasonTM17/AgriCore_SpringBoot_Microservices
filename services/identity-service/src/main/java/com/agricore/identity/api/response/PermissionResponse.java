package com.agricore.identity.api.response;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String name,
        String description,
        Instant createdAt
) {
}
