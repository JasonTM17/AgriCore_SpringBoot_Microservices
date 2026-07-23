package com.agricore.cropcatalog.infrastructure.persistence;

import com.agricore.cropcatalog.infrastructure.persistence.entity.CareRecommendationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CareRecommendationJpaRepository extends JpaRepository<CareRecommendationEntity, UUID> {

    List<CareRecommendationEntity> findAllByCropIdOrderBySortOrderAscIdAsc(UUID cropId);
}
