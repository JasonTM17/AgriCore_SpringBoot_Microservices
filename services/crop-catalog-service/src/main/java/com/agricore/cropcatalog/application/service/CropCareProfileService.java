package com.agricore.cropcatalog.application.service;

import com.agricore.cropcatalog.api.response.CareRecommendationResponse;
import com.agricore.cropcatalog.api.response.CommonDiseaseResponse;
import com.agricore.cropcatalog.api.response.CropCareProfileResponse;
import com.agricore.cropcatalog.api.response.GrowthRequirementResponse;
import com.agricore.cropcatalog.infrastructure.persistence.CareRecommendationJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.CommonDiseaseJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.CropJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.GrowthRequirementJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CareRecommendationEntity;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CommonDiseaseEntity;
import com.agricore.cropcatalog.infrastructure.persistence.entity.GrowthRequirementEntity;
import com.agricore.cropcatalog.domain.exception.CatalogException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CropCareProfileService {

    private final CropJpaRepository cropRepository;
    private final GrowthRequirementJpaRepository growthRequirementRepository;
    private final CommonDiseaseJpaRepository diseaseRepository;
    private final CareRecommendationJpaRepository recommendationRepository;

    public CropCareProfileService(
            CropJpaRepository cropRepository,
            GrowthRequirementJpaRepository growthRequirementRepository,
            CommonDiseaseJpaRepository diseaseRepository,
            CareRecommendationJpaRepository recommendationRepository
    ) {
        this.cropRepository = cropRepository;
        this.growthRequirementRepository = growthRequirementRepository;
        this.diseaseRepository = diseaseRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional(readOnly = true)
    public CropCareProfileResponse get(UUID cropId) {
        if (!cropRepository.existsById(cropId)) {
            throw cropNotFound();
        }
        GrowthRequirementResponse growthRequirement = growthRequirementRepository.findById(cropId)
                .map(CropCareProfileService::toResponse)
                .orElse(null);
        return new CropCareProfileResponse(
                cropId,
                growthRequirement,
                diseaseRepository.findAllByCropIdOrderByNameAscIdAsc(cropId).stream()
                        .map(CropCareProfileService::toResponse)
                        .toList(),
                recommendationRepository.findAllByCropIdOrderBySortOrderAscIdAsc(cropId).stream()
                        .map(CropCareProfileService::toResponse)
                        .toList()
        );
    }

    private static GrowthRequirementResponse toResponse(GrowthRequirementEntity requirement) {
        return new GrowthRequirementResponse(
                requirement.getIrrigationIntervalDaysMin(),
                requirement.getIrrigationIntervalDaysMax(),
                requirement.getFertilizationIntervalDaysMin(),
                requirement.getFertilizationIntervalDaysMax(),
                requirement.getWaterRequirementMmPerWeek(),
                requirement.getNotes(),
                requirement.getUpdatedAt(),
                requirement.getVersion(),
                requirement.getUpdatedBy()
        );
    }

    private static CommonDiseaseResponse toResponse(CommonDiseaseEntity disease) {
        return new CommonDiseaseResponse(
                disease.getId(),
                disease.getCode(),
                disease.getName(),
                disease.getSymptoms(),
                disease.getPrevention(),
                disease.getTreatment(),
                disease.getCreatedAt()
        );
    }

    private static CareRecommendationResponse toResponse(CareRecommendationEntity recommendation) {
        return new CareRecommendationResponse(
                recommendation.getId(),
                recommendation.getCategory(),
                recommendation.getTitle(),
                recommendation.getDescription(),
                recommendation.getGrowthStage(),
                recommendation.getSortOrder(),
                recommendation.getCreatedAt()
        );
    }

    private static CatalogException cropNotFound() {
        return new CatalogException("CROP_NOT_FOUND", "Crop not found", 404);
    }
}
