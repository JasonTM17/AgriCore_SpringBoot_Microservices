package com.agricore.traceability.infrastructure.persistence;

import com.agricore.traceability.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Pk> {
    Optional<ProcessedEventEntity> findByEventIdAndConsumerName(String eventId, String consumerName);
    Optional<ProcessedEventEntity> findFirstByEventIdIgnoreCaseAndConsumerName(String eventId, String consumerName);

    default Optional<ProcessedEventEntity> findCanonicalOrLegacy(
            String canonicalEventId,
            String consumerName
    ) {
        return findByEventIdAndConsumerName(canonicalEventId, consumerName)
                .or(() -> findFirstByEventIdIgnoreCaseAndConsumerName(
                        canonicalEventId,
                        consumerName
                ));
    }
}
