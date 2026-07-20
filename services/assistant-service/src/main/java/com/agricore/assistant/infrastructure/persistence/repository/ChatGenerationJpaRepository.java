package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatGenerationJpaRepository extends JpaRepository<ChatGenerationEntity, UUID> {

    Optional<ChatGenerationEntity> findByOwnerUserIdAndConversationIdAndIdempotencyKey(
            UUID ownerUserId,
            UUID conversationId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT generation
              FROM ChatGenerationEntity generation
             WHERE generation.ownerUserId = :ownerUserId
               AND generation.conversationId = :conversationId
               AND generation.idempotencyKey = :idempotencyKey
            """)
    Optional<ChatGenerationEntity> findByIdempotencyForUpdate(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("conversationId") UUID conversationId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT generation
              FROM ChatGenerationEntity generation
             WHERE generation.id = :generationId
               AND generation.conversationId = :conversationId
               AND generation.ownerUserId = :ownerUserId
            """)
    Optional<ChatGenerationEntity> findOwnedForUpdate(
            @Param("generationId") UUID generationId,
            @Param("conversationId") UUID conversationId,
            @Param("ownerUserId") UUID ownerUserId
    );

    Optional<ChatGenerationEntity> findByIdAndConversationIdAndOwnerUserId(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId
    );

    Optional<ChatGenerationEntity> findFirstByConversationIdAndActiveConversationIdIsNotNull(
            UUID conversationId
    );
}
