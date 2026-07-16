package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.SensorAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SensorAlertJpaRepository extends JpaRepository<SensorAlertEntity, UUID> {
    Optional<SensorAlertEntity> findFirstByFingerprintAndStatusOrderByCreatedAtDesc(String fingerprint, String status);
    long countByDeviceIdAndStatus(UUID deviceId, String status);
}
