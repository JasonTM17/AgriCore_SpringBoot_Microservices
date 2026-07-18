package com.agricore.farm.infrastructure.persistence;

import com.agricore.farm.infrastructure.persistence.entity.FarmMembershipEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmMembershipJpaRepository extends JpaRepository<FarmMembershipEntity, UUID> {

    boolean existsByFarmIdAndSubject(UUID farmId, String subject);

    Optional<FarmMembershipEntity> findByIdAndFarmId(UUID id, UUID farmId);

    Page<FarmMembershipEntity> findByFarmId(UUID farmId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m FROM FarmMembershipEntity m
            WHERE m.farmId = :farmId
            ORDER BY m.id
            """)
    List<FarmMembershipEntity> findByFarmIdForUpdate(@Param("farmId") UUID farmId);
}
