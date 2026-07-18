package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findByOwnerUserIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID ownerUserId);

    Optional<ConversationEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
