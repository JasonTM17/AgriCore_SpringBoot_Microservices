package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.infrastructure.persistence.entity.AssistantAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AssistantAuditEventJpaRepository extends JpaRepository<AssistantAuditEventEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH expired AS (
                SELECT id
                  FROM assistant_audit_events
                 WHERE retain_until <= :now
                 ORDER BY retain_until, id
                 LIMIT :batchSize
                 FOR UPDATE SKIP LOCKED
            )
            DELETE FROM assistant_audit_events target
             USING expired
             WHERE target.id = expired.id
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
