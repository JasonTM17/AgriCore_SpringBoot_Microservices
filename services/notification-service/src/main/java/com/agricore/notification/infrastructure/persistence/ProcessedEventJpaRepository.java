package com.agricore.notification.infrastructure.persistence;

import com.agricore.notification.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
