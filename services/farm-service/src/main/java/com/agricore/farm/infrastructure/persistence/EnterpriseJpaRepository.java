package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.EnterpriseStatus;
import com.agricore.farm.infrastructure.persistence.entity.EnterpriseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EnterpriseJpaRepository extends JpaRepository<EnterpriseEntity, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByTaxCodeIgnoreCase(String taxCode);

    boolean existsByTaxCodeIgnoreCaseAndIdNot(String taxCode, UUID id);

    Optional<EnterpriseEntity> findById(UUID id);

    @Query("""
            SELECT enterprise
            FROM EnterpriseEntity enterprise
            WHERE (:status IS NULL OR enterprise.status = :status)
              AND (
                :province IS NULL
                OR LOWER(enterprise.province) LIKE LOWER(CONCAT('%', :province, '%')) ESCAPE '!'
              )
              AND (
                :query IS NULL
                OR LOWER(enterprise.code) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR LOWER(enterprise.name) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR LOWER(enterprise.legalName) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR LOWER(enterprise.taxCode) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
              )
            """)
    Page<EnterpriseEntity> search(
            EnterpriseStatus status,
            String province,
            String query,
            Pageable pageable
    );
}
