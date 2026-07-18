package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.domain.model.FarmStatus;
import com.agricore.farm.infrastructure.persistence.entity.FarmEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FarmJpaRepository extends JpaRepository<FarmEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<FarmEntity> findByCodeIgnoreCase(String code);

    @Query("""
            SELECT f FROM FarmEntity f
            WHERE (:status IS NULL OR f.status = :status)
              AND (:province IS NULL OR LOWER(f.province) LIKE LOWER(CONCAT('%', :province, '%')))
            """)
    Page<FarmEntity> search(
            @Param("province") String province,
            @Param("status") FarmStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT f FROM FarmEntity f
            WHERE EXISTS (
                SELECT m.id FROM FarmMembershipEntity m
                WHERE m.farmId = f.id AND m.subject = :subject
            )
              AND (:status IS NULL OR f.status = :status)
              AND (:province IS NULL OR LOWER(f.province) LIKE LOWER(CONCAT('%', :province, '%')))
            """)
    Page<FarmEntity> searchAccessible(
            @Param("subject") String subject,
            @Param("province") String province,
            @Param("status") FarmStatus status,
            Pageable pageable
    );
}
