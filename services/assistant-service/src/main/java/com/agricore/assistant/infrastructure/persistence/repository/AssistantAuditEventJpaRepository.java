package com.agricore.assistant.infrastructure.persistence.repository;

import com.agricore.assistant.infrastructure.persistence.entity.AssistantAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssistantAuditEventJpaRepository extends JpaRepository<AssistantAuditEventEntity, UUID> {
}
