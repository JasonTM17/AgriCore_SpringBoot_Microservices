package com.agricore.cropcatalog.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcatalog.api.response.CropResponse;
import com.agricore.cropcatalog.infrastructure.persistence.CropJpaRepository;
import com.agricore.cropcatalog.infrastructure.persistence.entity.CropEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CropCatalogService {

    private final CropJpaRepository cropRepository;

    public CropCatalogService(CropJpaRepository cropRepository) {
        this.cropRepository = cropRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CropResponse> list(String category, String q, Pageable pageable) {
        Page<CropEntity> page;
        if (StringUtils.hasText(category)) {
            page = cropRepository.findByCategoryIgnoreCase(category, pageable);
        } else if (StringUtils.hasText(q)) {
            page = cropRepository.findByNameContainingIgnoreCase(q, pageable);
        } else {
            page = cropRepository.findAll(pageable);
        }
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public CropResponse get(UUID id) {
        return cropRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop not found"));
    }

    @Transactional(readOnly = true)
    public CropResponse getByCode(String code) {
        return cropRepository.findByCodeIgnoreCase(code).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop not found"));
    }

    private CropResponse toResponse(CropEntity c) {
        return new CropResponse(
                c.getId(), c.getCode(), c.getName(), c.getScientificName(), c.getCategory(),
                c.getGrowthDaysMin(), c.getGrowthDaysMax(), c.getTempMinC(), c.getTempMaxC(),
                c.getHumidityMinPct(), c.getHumidityMaxPct(), c.getPhMin(), c.getPhMax(),
                c.getExpectedYieldPerHa(), c.getYieldUnit(), c.getDescription(), c.getCreatedAt()
        );
    }
}
