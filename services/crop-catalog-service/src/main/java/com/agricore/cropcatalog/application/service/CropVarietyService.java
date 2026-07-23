package com.agricore.cropcatalog.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcatalog.api.response.CropVarietyResponse;
import com.agricore.cropcatalog.domain.exception.CatalogException;
import com.agricore.cropcatalog.infrastructure.persistence.CropJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.CropVarietyJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CropVarietyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class CropVarietyService {

    private final CropVarietyJpaRepository varietyRepository;
    private final CropJpaRepository cropRepository;

    public CropVarietyService(
            CropVarietyJpaRepository varietyRepository,
            CropJpaRepository cropRepository
    ) {
        this.varietyRepository = varietyRepository;
        this.cropRepository = cropRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CropVarietyResponse> list(UUID cropId, String query, Pageable pageable) {
        if (!cropRepository.existsById(cropId)) {
            throw new CatalogException("CROP_NOT_FOUND", "Crop not found", 404);
        }
        Page<CropVarietyEntity> page = StringUtils.hasText(query)
                ? varietyRepository.searchByCropId(cropId, query.trim(), pageable)
                : varietyRepository.findByCropId(cropId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public CropVarietyResponse get(UUID varietyId) {
        return varietyRepository.findById(varietyId)
                .map(this::toResponse)
                .orElseThrow(() -> new CatalogException(
                        "CROP_VARIETY_NOT_FOUND",
                        "Crop variety not found",
                        404
                ));
    }

    private CropVarietyResponse toResponse(CropVarietyEntity variety) {
        return new CropVarietyResponse(
                variety.getId(),
                variety.getCropId(),
                variety.getCode(),
                variety.getName(),
                variety.getOrigin(),
                variety.getNotes(),
                variety.getCreatedAt()
        );
    }
}
