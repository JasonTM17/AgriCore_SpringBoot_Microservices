package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {
    Optional<DeviceEntity> findByDeviceCodeIgnoreCase(String deviceCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceEntity d where upper(d.deviceCode) = upper(:deviceCode)")
    Optional<DeviceEntity> findByDeviceCodeIgnoreCaseForUpdate(@Param("deviceCode") String deviceCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from DeviceEntity d
            where d.status = 'ACTIVE'
              and coalesce(d.lastSeenAt, d.createdAt) < :cutoff
            order by coalesce(d.lastSeenAt, d.createdAt), d.id
            """)
    List<DeviceEntity> findStaleActiveForUpdate(@Param("cutoff") Instant cutoff, Pageable pageable);

    boolean existsByDeviceCodeIgnoreCase(String deviceCode);
}
