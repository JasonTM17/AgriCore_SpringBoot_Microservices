package com.agricore.cropcatalog.application.service;

import com.agricore.cropcatalog.api.request.CareRecommendationInput;
import com.agricore.cropcatalog.api.request.CommonDiseaseInput;
import com.agricore.cropcatalog.api.request.GrowthRequirementInput;
import com.agricore.cropcatalog.api.request.ReplaceCropCareProfileRequest;
import com.agricore.cropcatalog.api.response.CropCareProfileResponse;
import com.agricore.cropcatalog.domain.exception.CatalogException;
import com.agricore.cropcatalog.infrastructure.persistence.CareRecommendationJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.CommonDiseaseJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.CropJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.GrowthRequirementJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CareRecommendationEntity;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CommonDiseaseEntity;
import com.agricore.cropcatalog.infrastructure.persistence.entity.GrowthRequirementEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CropCareProfileManagementService {

    private final CropJpaRepository cropRepository;
    private final GrowthRequirementJpaRepository growthRepository;
    private final CommonDiseaseJpaRepository diseaseRepository;
    private final CareRecommendationJpaRepository recommendationRepository;
    private final CropCareProfileService queryService;

    public CropCareProfileManagementService(
            CropJpaRepository cropRepository,
            GrowthRequirementJpaRepository growthRepository,
            CommonDiseaseJpaRepository diseaseRepository,
            CareRecommendationJpaRepository recommendationRepository,
            CropCareProfileService queryService
    ) {
        this.cropRepository = cropRepository;
        this.growthRepository = growthRepository;
        this.diseaseRepository = diseaseRepository;
        this.recommendationRepository = recommendationRepository;
        this.queryService = queryService;
    }

    @Transactional
    public CropCareProfileResponse replace(
            UUID cropId,
            ReplaceCropCareProfileRequest request,
            String updatedBy
    ) {
        requireCrop(cropId);
        validate(request);
        String actor = normalizedActor(updatedBy);
        Instant now = Instant.now();
        GrowthRequirementEntity growth = growthRepository.findById(cropId)
                .orElseGet(() -> GrowthRequirementEntity.create(cropId));
        if (growth.getVersion() != request.version()) {
            throw new CatalogException(
                    "CARE_PROFILE_VERSION_CONFLICT",
                    "Crop care profile changed; reload the latest version before retrying",
                    409
            );
        }

        apply(growth, request.growthRequirement(), actor, now);
        reconcileDiseases(cropId, request, now);
        reconcileRecommendations(cropId, request, now);
        growthRepository.saveAndFlush(growth);
        return queryService.get(cropId);
    }

    private void reconcileDiseases(UUID cropId, ReplaceCropCareProfileRequest request, Instant now) {
        Map<String, CommonDiseaseEntity> existing = diseaseRepository
                .findAllByCropIdOrderByNameAscIdAsc(cropId).stream()
                .collect(Collectors.toMap(CommonDiseaseEntity::getCode, Function.identity()));
        var retained = new HashSet<CommonDiseaseEntity>();
        for (CommonDiseaseInput input : request.commonDiseases()) {
            String code = input.code().trim().toUpperCase(Locale.ROOT);
            CommonDiseaseEntity disease = existing.getOrDefault(
                    code,
                    CommonDiseaseEntity.create(UUID.randomUUID(), cropId, now)
            );
            disease.update(
                    code,
                    input.name().trim(),
                    input.symptoms().trim(),
                    input.prevention().trim(),
                    input.treatment().trim()
            );
            retained.add(disease);
        }
        diseaseRepository.deleteAll(existing.values().stream().filter(item -> !retained.contains(item)).toList());
        diseaseRepository.saveAll(retained);
    }

    private void reconcileRecommendations(UUID cropId, ReplaceCropCareProfileRequest request, Instant now) {
        Map<String, CareRecommendationEntity> existing = recommendationRepository
                .findAllByCropIdOrderBySortOrderAscIdAsc(cropId).stream()
                .collect(Collectors.toMap(CareRecommendationEntity::getTitle, Function.identity()));
        var retained = new HashSet<CareRecommendationEntity>();
        for (CareRecommendationInput input : request.recommendations()) {
            String title = input.title().trim();
            CareRecommendationEntity recommendation = existing.getOrDefault(
                    title,
                    CareRecommendationEntity.create(UUID.randomUUID(), cropId, now)
            );
            recommendation.update(
                    input.category().name(),
                    title,
                    input.description().trim(),
                    trimToNull(input.growthStage()),
                    input.sortOrder()
            );
            retained.add(recommendation);
        }
        recommendationRepository.deleteAll(
                existing.values().stream().filter(item -> !retained.contains(item)).toList()
        );
        recommendationRepository.saveAll(retained);
    }

    private void requireCrop(UUID cropId) {
        if (!cropRepository.existsById(cropId)) {
            throw new CatalogException("CROP_NOT_FOUND", "Crop not found", 404);
        }
    }

    private static void validate(ReplaceCropCareProfileRequest request) {
        GrowthRequirementInput growth = request.growthRequirement();
        if (growth.irrigationIntervalDaysMin() > growth.irrigationIntervalDaysMax()
                || growth.fertilizationIntervalDaysMin() > growth.fertilizationIntervalDaysMax()) {
            throw new CatalogException(
                    "INVALID_GROWTH_INTERVAL",
                    "Growth interval minimum must not exceed its maximum",
                    400
            );
        }
        requireUnique(
                request.commonDiseases().stream().map(item -> item.code().trim().toUpperCase(Locale.ROOT)).toList(),
                "DUPLICATE_DISEASE_CODE",
                "Disease codes must be unique within a crop care profile"
        );
        requireUnique(
                request.recommendations().stream().map(item -> item.title().trim().toLowerCase(Locale.ROOT)).toList(),
                "DUPLICATE_RECOMMENDATION_TITLE",
                "Recommendation titles must be unique within a crop care profile"
        );
        if (request.recommendations().stream().anyMatch(item -> item.sortOrder() < 0)) {
            throw new CatalogException("INVALID_RECOMMENDATION_ORDER", "Recommendation order must not be negative", 400);
        }
    }

    private static void requireUnique(Iterable<String> values, String code, String message) {
        var unique = new HashSet<String>();
        for (String value : values) {
            if (!unique.add(value)) {
                throw new CatalogException(code, message, 400);
            }
        }
    }

    private static void apply(
            GrowthRequirementEntity entity,
            GrowthRequirementInput input,
            String actor,
            Instant now
    ) {
        entity.update(
                input.irrigationIntervalDaysMin(),
                input.irrigationIntervalDaysMax(),
                input.fertilizationIntervalDaysMin(),
                input.fertilizationIntervalDaysMax(),
                input.waterRequirementMmPerWeek(),
                trimToNull(input.notes()),
                actor,
                now
        );
    }

    private static String normalizedActor(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new CatalogException("INVALID_ACTOR", "Authenticated actor is invalid", 401);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
