package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface GenerationEventJpaRepository extends JpaRepository<GenerationEventEntity, UUID> {

    @Query("""
            SELECT event
              FROM GenerationEventEntity event
             WHERE event.generationId = :generationId
               AND event.sequenceNo > :afterSequence
               AND (event.expiresAt IS NULL OR event.expiresAt > :now)
             ORDER BY event.sequenceNo ASC
            """)
    List<GenerationEventEntity> findAfter(
            @Param("generationId") UUID generationId,
            @Param("afterSequence") long afterSequence,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH expired AS (
                SELECT id
                  FROM generation_events
                 WHERE expires_at IS NOT NULL
                   AND expires_at <= :now
                 ORDER BY expires_at, id
                 LIMIT :batchSize
                 FOR UPDATE SKIP LOCKED
            )
            DELETE FROM generation_events target
             USING expired
             WHERE target.id = expired.id
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
