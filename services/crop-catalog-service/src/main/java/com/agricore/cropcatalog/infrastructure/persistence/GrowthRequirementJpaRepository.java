package com.agricore.cropcatalog.infrastructure.persistence;

import com.agricore.cropcatalog.infrastructure.persistence.entity.GrowthRequirementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrowthRequirementJpaRepository extends JpaRepository<GrowthRequirementEntity, UUID> {
}
