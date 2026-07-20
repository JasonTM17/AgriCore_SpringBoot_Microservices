package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.infrastructure.persistence.entity.AssistantAuditEventEntity;
import com.agricore.assistant.infrastructure.persistence.repository.AssistantAuditEventJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantAuditPersistenceAdapter implements AssistantAuditRepository {

    private final AssistantAuditEventJpaRepository repository;

    public AssistantAuditPersistenceAdapter(AssistantAuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(AssistantAuditEvent event) {
        AssistantAuditEventEntity entity = new AssistantAuditEventEntity();
        entity.setId(event.id());
        entity.setActorSubject(event.actorSubject());
        entity.setOwnerUserId(event.ownerUserId());
        entity.setFarmId(event.farmId());
        entity.setConversationId(event.conversationId());
        entity.setGenerationId(event.generationId());
        entity.setAction(event.action());
        entity.setOutcome(event.outcome());
        entity.setReasonCode(event.reasonCode());
        entity.setTraceId(event.traceId());
        entity.setCorrelationId(event.correlationId());
        entity.setMetadata(event.metadata());
        entity.setCreatedAt(event.createdAt());
        entity.setRetainUntil(event.retainUntil());
        repository.save(entity);
    }
}
