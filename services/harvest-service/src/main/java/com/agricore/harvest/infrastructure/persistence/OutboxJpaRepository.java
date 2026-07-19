package com.agricore.harvest.infrastructure.persistence;

import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findUnpublished(Pageable pageable);

    Optional<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType,
            String aggregateId,
            String eventType
    );
}
