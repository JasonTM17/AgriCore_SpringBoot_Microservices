package com.agricore.harvest.infrastructure.persistence;

import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant currentTimestamp();

    @Query("""
            SELECT event.id FROM OutboxEventEntity event
            WHERE event.publishedAt IS NULL
              AND event.quarantinedAt IS NULL
              AND (event.nextAttemptAt IS NULL OR event.nextAttemptAt <= :now)
            ORDER BY event.createdAt ASC
            """)
    List<UUID> findUnpublishedEventIds(@Param("now") Instant now, Pageable pageable);

    long countByPublishedAtIsNull();

    long countByPublishedAtIsNullAndQuarantinedAtIsNull();

    long countByQuarantinedAtIsNotNull();

    @Query(value = "SELECT * FROM outbox_events WHERE id = :eventId FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<OutboxEventEntity> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE id = :eventId
              AND published_at IS NULL
              AND quarantined_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEventEntity> findByIdForPublish(
            @Param("eventId") UUID eventId,
            @Param("now") Instant now
    );

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
