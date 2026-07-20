package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import org.springframework.stereotype.Component;

@Component
public class GenerationPersistenceMapper {

    private final RoleSnapshotJsonCodec roleSnapshotCodec;

    public GenerationPersistenceMapper(RoleSnapshotJsonCodec roleSnapshotCodec) {
        this.roleSnapshotCodec = roleSnapshotCodec;
    }

    public java.util.List<String> decodeRoleSnapshot(String value) {
        return roleSnapshotCodec.decode(value);
    }

    public AssistantGeneration toDomain(ChatGenerationEntity entity) {
        return new AssistantGeneration(
                entity.getId(), entity.getConversationId(), entity.getOwnerUserId(), entity.getFarmId(),
                entity.getIdempotencyKey(), entity.getRequestHash(), entity.getStatus(),
                entity.getActiveConversationId(), entity.getErrorCode(), roleSnapshotCodec.decode(entity.getRoleSnapshot()),
                entity.getNextEventSequence(), entity.getProvider(), entity.getModel(), entity.getInputTokens(),
                entity.getOutputTokens(), entity.getFirstTokenLatencyMs(), entity.getProviderLatencyMs(),
                entity.getTotalLatencyMs(), entity.getQueuedAt(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getStartedAt(), entity.getFirstTokenAt(), entity.getCancelRequestedAt(),
                entity.getCancelledAt(), entity.getLeaseToken(), entity.getLeaseExpiresAt(),
                entity.getAttemptCount(), entity.getVersion(), entity.getCompletedAt(), entity.getPurgeAfter()
        );
    }

    public AssistantGenerationEvent toDomain(GenerationEventEntity entity) {
        return new AssistantGenerationEvent(
                entity.getId(), entity.getGenerationId(), entity.getSequenceNo(), entity.getEventType(),
                entity.getPayload(), entity.getCreatedAt(), entity.getExpiresAt()
        );
    }

    public AssistantMessage toDomain(ConversationMessageEntity entity) {
        return new AssistantMessage(
                entity.getId(), entity.getConversationId(), entity.getGenerationId(), entity.getSequenceNo(),
                entity.getRole(), entity.getContent(), entity.getTokenCount(), entity.getCreatedAt()
        );
    }

    public ChatGenerationEntity toEntity(AssistantGeneration generation) {
        ChatGenerationEntity entity = new ChatGenerationEntity();
        entity.setId(generation.id());
        entity.setConversationId(generation.conversationId());
        entity.setOwnerUserId(generation.ownerUserId());
        entity.setActiveConversationId(generation.activeConversationId());
        entity.setFarmId(generation.farmId());
        entity.setIdempotencyKey(generation.idempotencyKey());
        entity.setRequestHash(generation.requestHash());
        entity.setStatus(generation.status());
        entity.setErrorCode(generation.errorCode());
        entity.setRoleSnapshot(roleSnapshotCodec.encode(generation.roleSnapshot()));
        entity.setNextEventSequence(generation.nextEventSequence());
        entity.setProvider(generation.provider());
        entity.setModel(generation.model());
        entity.setInputTokens(generation.inputTokens());
        entity.setOutputTokens(generation.outputTokens());
        entity.setFirstTokenLatencyMs(generation.firstTokenLatencyMs());
        entity.setProviderLatencyMs(generation.providerLatencyMs());
        entity.setTotalLatencyMs(generation.totalLatencyMs());
        entity.setQueuedAt(generation.queuedAt());
        entity.setCreatedAt(generation.createdAt());
        entity.setUpdatedAt(generation.updatedAt());
        entity.setStartedAt(generation.startedAt());
        entity.setFirstTokenAt(generation.firstTokenAt());
        entity.setCancelRequestedAt(generation.cancelRequestedAt());
        entity.setCancelledAt(generation.cancelledAt());
        entity.setLeaseToken(generation.leaseToken());
        entity.setLeaseExpiresAt(generation.leaseExpiresAt());
        entity.setAttemptCount(generation.attemptCount());
        entity.setVersion(generation.version());
        entity.setCompletedAt(generation.completedAt());
        entity.setPurgeAfter(generation.purgeAfter());
        return entity;
    }

    public GenerationEventEntity toEntity(AssistantGenerationEvent event) {
        GenerationEventEntity entity = new GenerationEventEntity();
        entity.setId(event.id());
        entity.setGenerationId(event.generationId());
        entity.setSequenceNo(event.sequenceNo());
        entity.setEventType(event.eventType());
        entity.setPayload(event.payload());
        entity.setCreatedAt(event.createdAt());
        entity.setExpiresAt(event.expiresAt());
        return entity;
    }

    public ConversationMessageEntity toEntity(AssistantMessage message) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(message.id());
        entity.setConversationId(message.conversationId());
        entity.setGenerationId(message.generationId());
        entity.setSequenceNo(message.sequenceNo());
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setTokenCount(message.tokenCount());
        entity.setCreatedAt(message.createdAt());
        return entity;
    }
}
