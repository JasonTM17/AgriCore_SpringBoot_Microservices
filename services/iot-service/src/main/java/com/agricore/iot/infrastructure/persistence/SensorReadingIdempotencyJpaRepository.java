package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.SensorReadingIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SensorReadingIdempotencyJpaRepository
        extends JpaRepository<SensorReadingIdempotencyEntity, UUID> {
}
