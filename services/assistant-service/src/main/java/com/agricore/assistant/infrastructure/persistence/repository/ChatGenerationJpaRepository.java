package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatGenerationJpaRepository extends JpaRepository<ChatGenerationEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT generation FROM ChatGenerationEntity generation WHERE generation.id = :generationId")
    Optional<ChatGenerationEntity> findByIdForUpdate(@Param("generationId") UUID generationId);

    @Query("""
            SELECT new com.agricore.assistant.infrastructure.persistence.repository.GenerationExecutionReference(
                generation.conversationId,
                generation.ownerUserId,
                generation.status,
                generation.leaseToken
            )
              FROM ChatGenerationEntity generation
             WHERE generation.id = :generationId
            """)
    Optional<GenerationExecutionReference> findExecutionReference(
            @Param("generationId") UUID generationId
    );

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

    Optional<ChatGenerationEntity> findFirstByConversationIdAndOwnerUserIdAndActiveConversationIdIsNotNull(
            UUID conversationId,
            UUID ownerUserId
    );

    @Query("""
            SELECT generation.id
              FROM ChatGenerationEntity generation
             WHERE generation.status = :status
             ORDER BY generation.queuedAt ASC, generation.id ASC
            """)
    List<UUID> findIdsByStatus(
            @Param("status") GenerationStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT generation
              FROM ChatGenerationEntity generation
             WHERE generation.status IN :statuses
               AND generation.leaseExpiresAt IS NOT NULL
               AND generation.leaseExpiresAt <= :now
             ORDER BY generation.leaseExpiresAt ASC, generation.queuedAt ASC, generation.id ASC
            """)
    List<ChatGenerationEntity> findExpiredLeasesForUpdate(
            @Param("statuses") List<GenerationStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );
}
