package com.agricore.notification.infrastructure.persistence;

import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    long countBySourceEventId(UUID sourceEventId);

    Optional<NotificationEntity> findBySourceEventId(UUID sourceEventId);

    Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationEntity n where n.id = :id")
    Optional<NotificationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select n.id from NotificationEntity n
            where n.status = 'REQUESTED'
               or (n.status = 'DELIVERING'
                   and upper(n.channel) = 'IN_APP'
                   and n.deliveryStartedAt < :staleBefore)
            order by n.createdAt
            """)
    List<UUID> findRecoverableIds(@Param("staleBefore") Instant staleBefore, Pageable pageable);

    @Query("""
            select n.id from NotificationEntity n
            where n.status = 'DELIVERING'
              and upper(n.channel) <> 'IN_APP'
              and n.deliveryStartedAt < :staleBefore
            order by n.createdAt
            """)
    List<UUID> findAmbiguousExternalDeliveryIds(
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );
}
