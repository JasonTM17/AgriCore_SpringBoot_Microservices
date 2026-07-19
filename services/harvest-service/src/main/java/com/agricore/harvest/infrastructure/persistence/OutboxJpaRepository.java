package com.agricore.harvest.infrastructure.persistence;

import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT o.id FROM OutboxEventEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<UUID> findUnpublishedEventIds(Pageable pageable);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :eventId FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<OutboxEventEntity> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :eventId FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutboxEventEntity> findByIdForPublish(@Param("eventId") UUID eventId);

    Optional<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType,
            String aggregateId,
            String eventType
    );

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE aggregate_type = :aggregateType
              AND aggregate_id = :aggregateId
              AND event_type = :eventType
            FOR UPDATE NOWAIT
            """, nativeQuery = true)
    Optional<OutboxEventEntity> findByAggregateTypeAndAggregateIdAndEventTypeForUpdate(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("eventType") String eventType
    );
}
