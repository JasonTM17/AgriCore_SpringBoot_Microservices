package com.agricore.traceability.infrastructure.persistence;

import com.agricore.traceability.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Pk> {
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
