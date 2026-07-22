package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT event.id FROM OutboxEventEntity event WHERE event.publishedAt IS NULL ORDER BY event.createdAt ASC")
    List<UUID> findUnpublishedEventIds(Pageable pageable);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :eventId FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutboxEventEntity> findByIdForPublish(@Param("eventId") UUID eventId);

    long countByPublishedAtIsNull();
}
