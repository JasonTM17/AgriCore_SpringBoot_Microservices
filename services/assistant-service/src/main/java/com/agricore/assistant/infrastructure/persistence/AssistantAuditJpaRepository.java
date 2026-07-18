package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.infrastructure.persistence.entity.AssistantAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssistantAuditJpaRepository extends JpaRepository<AssistantAuditEntity, UUID> {
}
