package com.agricore.cropcatalog.api.response;

import java.util.List;
import java.util.UUID;

public record CropCareProfileResponse(
        UUID cropId,
        GrowthRequirementResponse growthRequirement,
        List<CommonDiseaseResponse> commonDiseases,
        List<CareRecommendationResponse> recommendations
) {
}
