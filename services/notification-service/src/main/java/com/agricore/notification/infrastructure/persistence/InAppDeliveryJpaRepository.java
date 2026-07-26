package com.agricore.notification.infrastructure.persistence;

import com.agricore.notification.infrastructure.persistence.entity.InAppDeliveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InAppDeliveryJpaRepository extends JpaRepository<InAppDeliveryEntity, UUID> {

    Page<InAppDeliveryEntity> findByRecipient(String recipient, Pageable pageable);
}
