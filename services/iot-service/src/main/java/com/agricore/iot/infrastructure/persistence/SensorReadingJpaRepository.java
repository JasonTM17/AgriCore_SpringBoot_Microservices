package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.SensorReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SensorReadingJpaRepository extends JpaRepository<SensorReadingEntity, UUID> {
}
