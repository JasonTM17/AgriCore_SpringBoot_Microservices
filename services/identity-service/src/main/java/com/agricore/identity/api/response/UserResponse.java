package com.agricore.identity.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String status,
        List<String> roles,
        Instant lastLoginAt,
        Instant createdAt
) {
}
