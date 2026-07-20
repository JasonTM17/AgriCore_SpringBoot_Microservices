package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.domain.model.MessageRole;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationMessageJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GenerationPersistenceAdapter implements GenerationRepository {

    private final ConversationJpaRepository conversationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;

    public GenerationPersistenceAdapter(
            ConversationJpaRepository conversationRepository,
            ConversationMessageJpaRepository messageRepository,
            ChatGenerationJpaRepository generationRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GenerationSubmissionResult> findIdempotent(
            UUID conversationId,
            UUID ownerUserId,
            String idempotencyKey,
            String requestHash
    ) {
        return generationRepository.findByOwnerUserIdAndConversationIdAndIdempotencyKey(
                        ownerUserId, conversationId, idempotencyKey)
                .map(generation -> {
                    if (!requestHash.equalsIgnoreCase(generation.getRequestHash())) {
                        throw AssistantException.idempotencyKeyReused();
                    }
                    AssistantMessage userMessage = messageRepository
                            .findByGenerationIdAndRole(generation.getId(), MessageRole.USER)
                            .map(mapper::toDomain)
                            .orElse(null);
                    return new GenerationSubmissionResult(mapper.toDomain(generation), userMessage, true);
                });
    }

    @Override
    @Transactional
    public GenerationSubmissionResult submit(GenerationSubmissionCommand command) {
        Objects.requireNonNull(command, "command is required");
        ConversationEntity conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(), command.ownerUserId())
                .orElseThrow(AssistantException::notFound);

        Optional<ChatGenerationEntity> existing = generationRepository.findByIdempotencyForUpdate(
                command.ownerUserId(), command.conversationId(), command.idempotencyKey());
        if (existing.isPresent()) {
            ChatGenerationEntity generation = existing.get();
            if (!command.requestHash().equalsIgnoreCase(generation.getRequestHash())) {
                throw AssistantException.idempotencyKeyReused();
            }
            AssistantMessage userMessage = messageRepository
                    .findByGenerationIdAndRole(generation.getId(), MessageRole.USER)
                    .map(mapper::toDomain)
                    .orElse(null);
            return new GenerationSubmissionResult(mapper.toDomain(generation), userMessage, true);
        }

        if (conversation.getStatus() != com.agricore.assistant.domain.model.ConversationStatus.OPEN) {
            throw AssistantException.conversationNotOpen();
        }

        if (generationRepository.findFirstByConversationIdAndActiveConversationIdIsNotNull(
                command.conversationId()).isPresent()) {
            throw AssistantException.generationAlreadyActive();
        }

        long messageSequence = conversation.getNextMessageSequence();
        UUID generationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AssistantGeneration generation = new AssistantGeneration(
                generationId,
                conversation.getId(),
                conversation.getOwnerUserId(),
                conversation.getFarmId(),
                command.idempotencyKey(),
                command.requestHash(),
                GenerationStatus.QUEUED,
                conversation.getId(),
                null,
                mapper.decodeRoleSnapshot(conversation.getRoleSnapshot()),
                1,
                command.provider(),
                command.model(),
                null,
                null,
                null,
                null,
                null,
                command.now(),
                command.now(),
                command.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                command.purgeAfter()
        );
        AssistantMessage userMessage = new AssistantMessage(
                messageId,
                conversation.getId(),
                generationId,
                messageSequence,
                MessageRole.USER,
                command.prompt(),
                null,
                command.now()
        );
        AssistantGenerationEvent queuedEvent = new AssistantGenerationEvent(
                UUID.randomUUID(), generationId, 0, GenerationEventType.STATUS,
                "{\"status\":\"QUEUED\"}", command.now(), command.eventExpiresAt()
        );

        conversation.setNextMessageSequence(messageSequence + 1);
        conversation.setUpdatedAt(command.now());
        generationRepository.save(mapper.toEntity(generation));
        messageRepository.save(mapper.toEntity(userMessage));
        eventRepository.save(mapper.toEntity(queuedEvent));
        generationRepository.flush();
        return new GenerationSubmissionResult(generation, userMessage, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssistantGeneration> findOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId
    ) {
        return generationRepository.findByIdAndConversationIdAndOwnerUserId(
                        generationId, conversationId, ownerUserId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantGenerationEvent> findEventsOwned(
            UUID generationId,
            UUID conversationId,
            UUID ownerUserId,
            long afterSequence,
            int limit,
            Instant now
    ) {
        if (afterSequence < -1 || limit < 1 || limit > 1000 || now == null) {
            throw new IllegalArgumentException("invalid event cursor, limit or clock");
        }
        if (generationRepository.findByIdAndConversationIdAndOwnerUserId(
                generationId, conversationId, ownerUserId).isEmpty()) {
            throw AssistantException.generationNotFound();
        }
        return eventRepository.findAfter(generationId, afterSequence, now, PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
