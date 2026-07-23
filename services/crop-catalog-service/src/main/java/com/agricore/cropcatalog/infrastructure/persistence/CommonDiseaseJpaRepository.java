package com.agricore.cropcatalog.infrastructure.persistence;

import com.agricore.cropcatalog.infrastructure.persistence.entity.CommonDiseaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommonDiseaseJpaRepository extends JpaRepository<CommonDiseaseEntity, UUID> {

    List<CommonDiseaseEntity> findAllByCropIdOrderByNameAscIdAsc(UUID cropId);
}
