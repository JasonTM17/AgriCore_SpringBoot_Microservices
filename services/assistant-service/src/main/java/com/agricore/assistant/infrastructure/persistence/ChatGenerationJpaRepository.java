package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatGenerationJpaRepository extends JpaRepository<ChatGenerationEntity, UUID> {
    Optional<ChatGenerationEntity> findByOwnerUserIdAndConversationIdAndIdempotencyKey(
            UUID ownerUserId,
            UUID conversationId,
            String idempotencyKey
    );

    Optional<ChatGenerationEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    long countByConversationIdAndStatusIn(UUID conversationId, Iterable<String> statuses);
}
