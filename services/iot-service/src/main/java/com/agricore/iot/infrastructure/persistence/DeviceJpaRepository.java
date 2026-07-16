package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {
    Optional<DeviceEntity> findByDeviceCodeIgnoreCase(String deviceCode);
    boolean existsByDeviceCodeIgnoreCase(String deviceCode);
}
