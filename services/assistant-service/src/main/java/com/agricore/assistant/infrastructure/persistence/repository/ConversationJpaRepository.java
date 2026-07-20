package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
}
