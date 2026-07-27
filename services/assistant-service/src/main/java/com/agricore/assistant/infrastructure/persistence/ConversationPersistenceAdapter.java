package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.ConversationArchiveResult;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.model.PageResult;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationMessageJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationPersistenceAdapter implements ConversationRepository {

    private final ConversationJpaRepository conversationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final RoleSnapshotJsonCodec roleSnapshotCodec;

    public ConversationPersistenceAdapter(
            ConversationJpaRepository conversationRepository,
            ConversationMessageJpaRepository messageRepository,
            RoleSnapshotJsonCodec roleSnapshotCodec
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.roleSnapshotCodec = roleSnapshotCodec;
    }

    @Override
    public AssistantConversation save(AssistantConversation conversation) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(conversation.id());
        entity.setOwnerUserId(conversation.ownerUserId());
        entity.setTitle(conversation.title());
        entity.setContextType(conversation.contextType());
        entity.setFarmId(conversation.farmId());
        entity.setStatus(conversation.status());
        entity.setRoleSnapshot(roleSnapshotCodec.encode(conversation.roleSnapshot()));
        entity.setNextMessageSequence(conversation.nextMessageSequence());
        entity.setVersion(conversation.version());
        entity.setCreatedAt(conversation.createdAt());
        entity.setUpdatedAt(conversation.updatedAt());
        entity.setArchivedAt(conversation.archivedAt());
        entity.setPurgeAfter(conversation.purgeAfter());
        return toDomain(conversationRepository.save(entity));
    }

    @Override
    public Optional<AssistantConversation> findOwned(UUID conversationId, UUID ownerUserId) {
        return conversationRepository.findByIdAndOwnerUserId(conversationId, ownerUserId)
                .map(this::toDomain);
    }

    @Override
    public PageResult<AssistantConversation> findOwnedByStatus(
            UUID ownerUserId,
            ConversationStatus status,
            PageQuery pageQuery
    ) {
        Page<ConversationEntity> page = conversationRepository.findByOwnerUserIdAndStatus(
                ownerUserId,
                status,
                PageRequest.of(pageQuery.page(), pageQuery.size(),
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))
        );
        return new PageResult<>(page.getContent().stream().map(this::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public PageResult<AssistantMessage> findMessages(
            UUID conversationId,
            UUID ownerUserId,
            PageQuery pageQuery
    ) {
        Page<ConversationMessageEntity> page = messageRepository.findOwnedByConversationId(
                conversationId,
                ownerUserId,
                PageRequest.of(pageQuery.page(), pageQuery.size(),
                        Sort.by(Sort.Order.asc("sequenceNo"), Sort.Order.asc("id")))
        );
        return new PageResult<>(page.getContent().stream().map(this::toMessage).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public ConversationArchiveResult archiveOwned(
            UUID conversationId,
            UUID ownerUserId,
            Instant archivedAt,
            Instant purgeAfter
    ) {
        int changed = conversationRepository.archiveOwned(conversationId, ownerUserId, archivedAt, purgeAfter);
        Optional<AssistantConversation> conversation = findOwned(conversationId, ownerUserId);
        return new ConversationArchiveResult(conversation.orElse(null), changed > 0);
    }

    private AssistantConversation toDomain(ConversationEntity entity) {
        return new AssistantConversation(
                entity.getId(), entity.getOwnerUserId(), entity.getTitle(), entity.getContextType(),
                entity.getFarmId(), entity.getStatus(), roleSnapshotCodec.decode(entity.getRoleSnapshot()),
                entity.getNextMessageSequence(), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getArchivedAt(), entity.getPurgeAfter()
        );
    }

    private AssistantMessage toMessage(ConversationMessageEntity entity) {
        return new AssistantMessage(
                entity.getId(), entity.getConversationId(), entity.getGenerationId(), entity.getSequenceNo(),
                entity.getRole(), entity.getContent(), entity.getTokenCount(), entity.getCreatedAt()
        );
    }
}
