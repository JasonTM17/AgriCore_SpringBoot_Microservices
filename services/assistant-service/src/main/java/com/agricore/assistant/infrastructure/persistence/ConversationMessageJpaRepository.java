package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageJpaRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
