package com.agricore.notification.infrastructure.persistence;

import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    long countBySourceEventId(UUID sourceEventId);
}
