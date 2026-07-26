package com.agricore.work.infrastructure.persistence;

import com.agricore.work.infrastructure.persistence.entity.OutboxEventEntity;
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
            SELECT o.id FROM OutboxEventEntity o
            WHERE o.publishedAt IS NULL
              AND o.quarantinedAt IS NULL
              AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ORDER BY o.createdAt ASC
            """)
    List<UUID> findUnpublishedEventIds(@Param("now") Instant now, Pageable pageable);

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
            @Param("now") java.time.Instant now
    );

    long countByPublishedAtIsNull();

    long countByPublishedAtIsNullAndQuarantinedAtIsNull();

    long countByQuarantinedAtIsNotNull();
}
