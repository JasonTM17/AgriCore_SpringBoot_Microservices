package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    Page<ConversationEntity> findByOwnerUserIdAndStatus(
            UUID ownerUserId,
            ConversationStatus status,
            Pageable pageable
    );

    java.util.Optional<ConversationEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT conversation
              FROM ConversationEntity conversation
             WHERE conversation.id = :conversationId
               AND conversation.ownerUserId = :ownerUserId
            """)
    java.util.Optional<ConversationEntity> findOwnedForUpdate(
            @Param("conversationId") UUID conversationId,
            @Param("ownerUserId") UUID ownerUserId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ConversationEntity conversation
               SET conversation.status = com.agricore.assistant.domain.model.ConversationStatus.ARCHIVED,
                   conversation.archivedAt = :archivedAt,
                   conversation.purgeAfter = :purgeAfter,
                   conversation.updatedAt = :archivedAt,
                   conversation.version = conversation.version + 1
             WHERE conversation.id = :conversationId
               AND conversation.ownerUserId = :ownerUserId
               AND conversation.status = com.agricore.assistant.domain.model.ConversationStatus.OPEN
            """)
    int archiveOwned(
            @Param("conversationId") UUID conversationId,
            @Param("ownerUserId") UUID ownerUserId,
            @Param("archivedAt") Instant archivedAt,
            @Param("purgeAfter") Instant purgeAfter
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH expired AS (
                SELECT id
                  FROM conversations
                 WHERE status = 'ARCHIVED'
                   AND purge_after IS NOT NULL
                   AND purge_after <= :now
                 ORDER BY purge_after, id
                 LIMIT :batchSize
                 FOR UPDATE SKIP LOCKED
            )
            DELETE FROM conversations target
             USING expired
             WHERE target.id = expired.id
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
