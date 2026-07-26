package com.agricore.identity.infrastructure.persistence;

import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("""
            SELECT o.id
            FROM OutboxEventEntity o
            WHERE o.publishedAt IS NULL
              AND (o.claimUntil IS NULL OR o.claimUntil <= :now)
            ORDER BY o.createdAt ASC
            """)
    List<UUID> findPublishableEventIds(@Param("now") Instant now, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity o
            SET o.claimToken = :claimToken,
                o.claimUntil = :claimUntil,
                o.publishAttempts = o.publishAttempts + 1
            WHERE o.id = :eventId
              AND o.publishedAt IS NULL
              AND (o.claimUntil IS NULL OR o.claimUntil <= :now)
            """)
    int claimForPublish(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("now") Instant now,
            @Param("claimUntil") Instant claimUntil
    );

    Optional<OutboxEventEntity> findByIdAndClaimToken(UUID eventId, UUID claimToken);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity o
            SET o.publishedAt = :publishedAt,
                o.lastError = NULL,
                o.claimToken = NULL,
                o.claimUntil = NULL
            WHERE o.id = :eventId
              AND o.publishedAt IS NULL
              AND o.claimToken = :claimToken
            """)
    int completePublish(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity o
            SET o.lastError = :lastError,
                o.claimToken = NULL,
                o.claimUntil = NULL
            WHERE o.id = :eventId
              AND o.publishedAt IS NULL
              AND o.claimToken = :claimToken
            """)
    int failPublish(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("lastError") String lastError
    );
}
