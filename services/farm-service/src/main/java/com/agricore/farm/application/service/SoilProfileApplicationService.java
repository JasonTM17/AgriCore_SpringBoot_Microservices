package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.CreateSoilProfileRequest;
import com.agricore.farm.api.request.UpdateSoilProfileRequest;
import com.agricore.farm.api.response.SoilProfileResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.domain.model.SoilProfileStatus;
import com.agricore.farm.domain.model.SoilTexture;
import com.agricore.farm.infrastructure.persistence.SoilProfileJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.PlotEntity;
import com.agricore.farm.infrastructure.persistence.entity.SoilProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class SoilProfileApplicationService {

    private final SoilProfileJpaRepository profileRepository;
    private final FarmResourceResolver resourceResolver;
    private final FarmAuthorizationService authorizationService;

    public SoilProfileApplicationService(
            SoilProfileJpaRepository profileRepository,
            FarmResourceResolver resourceResolver,
            FarmAuthorizationService authorizationService
    ) {
        this.profileRepository = profileRepository;
        this.resourceResolver = resourceResolver;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public SoilProfileResponse create(UUID plotId, CreateSoilProfileRequest request) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        String sampleCode = request.sampleCode().strip().toUpperCase(Locale.ROOT);
        if (profileRepository.existsByFarmIdAndPlotIdAndSampleCodeIgnoreCase(
                plot.getFarmId(),
                plotId,
                sampleCode
        )) {
            throw new FarmException(
                    "SOIL_PROFILE_SAMPLE_CODE_EXISTS",
                    "Soil sample code already exists for this plot",
                    409
            );
        }

        String actor = authorizationService.currentActor().subject();
        Instant now = Instant.now();
        SoilProfileEntity profile = new SoilProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setFarmId(plot.getFarmId());
        profile.setPlotId(plotId);
        profile.setSampleCode(sampleCode);
        profile.setSampledAt(request.sampledAt());
        profile.setSampleDepthCm(request.sampleDepthCm());
        profile.setTexture(SoilTexture.valueOf(request.texture().toUpperCase(Locale.ROOT)));
        profile.setPh(request.ph());
        profile.setOrganicMatterPercent(request.organicMatterPercent());
        profile.setNitrogenMgKg(request.nitrogenMgKg());
        profile.setPhosphorusMgKg(request.phosphorusMgKg());
        profile.setPotassiumMgKg(request.potassiumMgKg());
        profile.setMoisturePercent(request.moisturePercent());
        profile.setNotes(trimToNull(request.notes()));
        profile.setStatus(SoilProfileStatus.ACTIVE);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setCreatedBy(actor);
        profile.setUpdatedBy(actor);
        profileRepository.saveAndFlush(profile);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<SoilProfileResponse> list(
            UUID plotId,
            String status,
            LocalDate sampledFrom,
            LocalDate sampledTo,
            String query,
            Pageable pageable
    ) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        validateDateRange(sampledFrom, sampledTo);
        SoilProfileStatus statusFilter = StringUtils.hasText(status)
                ? SoilProfileStatus.valueOf(status.toUpperCase(Locale.ROOT))
                : null;
        String queryFilter = StringUtils.hasText(query) ? escapeLike(query.strip()) : null;
        Page<SoilProfileEntity> page = profileRepository.searchByPlot(
                plot.getFarmId(),
                plotId,
                statusFilter,
                sampledFrom,
                sampledTo,
                queryFilter,
                pageable
        );
        return PageResponse.of(
                page.getContent().stream().map(SoilProfileApplicationService::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public SoilProfileResponse get(UUID plotId, UUID profileId) {
        return toResponse(requireProfile(plotId, profileId));
    }

    @Transactional
    public SoilProfileResponse update(UUID plotId, UUID profileId, UpdateSoilProfileRequest request) {
        SoilProfileEntity profile = requireProfile(plotId, profileId);
        if (request.statusPresent() && request.status() == null) {
            throw new FarmException(
                    "SOIL_PROFILE_STATUS_REQUIRED",
                    "Soil profile status cannot be null",
                    400
            );
        }
        if (!request.statusPresent() && !request.notesPresent()) {
            throw new FarmException(
                    "SOIL_PROFILE_EMPTY_UPDATE",
                    "Provide status or notes to update a soil profile",
                    400
            );
        }
        if (profile.getVersion() != request.version()) {
            throw new FarmException(
                    "SOIL_PROFILE_VERSION_CONFLICT",
                    "Soil profile changed; reload the latest version before retrying",
                    409
            );
        }
        if (request.statusPresent()) {
            profile.setStatus(SoilProfileStatus.valueOf(request.status().toUpperCase(Locale.ROOT)));
        }
        if (request.notesPresent()) {
            profile.setNotes(trimToNull(request.notes()));
        }
        profile.setUpdatedAt(Instant.now());
        profile.setUpdatedBy(authorizationService.currentActor().subject());
        profileRepository.saveAndFlush(profile);
        return toResponse(profile);
    }

    private SoilProfileEntity requireProfile(UUID plotId, UUID profileId) {
        PlotEntity plot = resourceResolver.requireAccessiblePlot(plotId);
        return profileRepository.findByFarmIdAndPlotIdAndId(plot.getFarmId(), plotId, profileId)
                .orElseThrow(() -> new FarmException(
                        "SOIL_PROFILE_NOT_FOUND",
                        "Soil profile not found",
                        404
                ));
    }

    private static void validateDateRange(LocalDate sampledFrom, LocalDate sampledTo) {
        if (sampledFrom != null && sampledTo != null && sampledFrom.isAfter(sampledTo)) {
            throw new FarmException(
                    "SOIL_PROFILE_DATE_RANGE_INVALID",
                    "sampledFrom must be on or before sampledTo",
                    400
            );
        }
    }

    private static SoilProfileResponse toResponse(SoilProfileEntity profile) {
        return new SoilProfileResponse(
                profile.getId(), profile.getFarmId(), profile.getPlotId(), profile.getSampleCode(),
                profile.getSampledAt(), profile.getSampleDepthCm(), profile.getTexture().name(),
                profile.getPh(), profile.getOrganicMatterPercent(), profile.getNitrogenMgKg(),
                profile.getPhosphorusMgKg(), profile.getPotassiumMgKg(), profile.getMoisturePercent(),
                profile.getNotes(), profile.getStatus().name(), profile.getCreatedAt(),
                profile.getUpdatedAt(), profile.getCreatedBy(), profile.getUpdatedBy(), profile.getVersion()
        );
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
