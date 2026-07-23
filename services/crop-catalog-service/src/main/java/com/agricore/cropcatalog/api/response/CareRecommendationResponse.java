package com.agricore.cropcatalog.api.response;

import java.time.Instant;
import java.util.UUID;

public record CareRecommendationResponse(
        UUID id,
        String category,
        String title,
        String description,
        String growthStage,
        int sortOrder,
        Instant createdAt
) {
}
