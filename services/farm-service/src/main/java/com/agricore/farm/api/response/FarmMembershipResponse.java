package com.agricore.farm.api.response;

import java.time.Instant;
import java.util.UUID;

public record FarmMembershipResponse(
        UUID id,
        UUID farmId,
        String subject,
        String grantedBy,
        Instant createdAt
) {
}
