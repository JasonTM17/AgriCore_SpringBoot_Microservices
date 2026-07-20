package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ConversationMessageJpaRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    @Query("""
            SELECT message
              FROM ConversationMessageEntity message
             WHERE message.conversationId = :conversationId
               AND EXISTS (
                   SELECT conversation.id
                     FROM ConversationEntity conversation
                    WHERE conversation.id = message.conversationId
                      AND conversation.ownerUserId = :ownerUserId
               )
            """)
    Page<ConversationMessageEntity> findOwnedByConversationId(
            @Param("conversationId") UUID conversationId,
            @Param("ownerUserId") UUID ownerUserId,
            Pageable pageable
    );
}
