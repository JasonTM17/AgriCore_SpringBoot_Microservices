package com.agricore.cropcatalog.api.response;

import java.time.Instant;
import java.util.UUID;

public record CommonDiseaseResponse(
        UUID id,
        String code,
        String name,
        String symptoms,
        String prevention,
        String treatment,
        Instant createdAt
) {
}
