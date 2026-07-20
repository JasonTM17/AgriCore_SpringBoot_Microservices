package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.application.model.GenerationCompletion;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.domain.model.MessageRole;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import com.agricore.assistant.infrastructure.persistence.repository.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationMessageJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationExecutionReference;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class GenerationTerminalTransitionStore {

    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final String FALLBACK_ERROR_CODE = "GENERATION_FAILED";

    private final ChatGenerationJpaRepository generationRepository;
    private final ConversationJpaRepository conversationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final GenerationPersistenceMapper mapper;
    private final GenerationEventFactory eventFactory;
    private final GenerationEventPayloadCodec payloadCodec;

    public GenerationTerminalTransitionStore(
            ChatGenerationJpaRepository generationRepository,
            ConversationJpaRepository conversationRepository,
            ConversationMessageJpaRepository messageRepository,
            GenerationEventJpaRepository eventRepository,
            GenerationPersistenceMapper mapper,
            GenerationEventFactory eventFactory,
            GenerationEventPayloadCodec payloadCodec
    ) {
        this.generationRepository = generationRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.eventFactory = eventFactory;
        this.payloadCodec = payloadCodec;
    }

    @Transactional
    public Optional<AssistantGeneration> complete(
            UUID generationId,
            UUID leaseToken,
            GenerationCompletion completion
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        Objects.requireNonNull(completion, "completion is required");
        GenerationExecutionReference reference = generationRepository
                .findExecutionReference(generationId).orElse(null);
        if (reference == null
                || reference.status() != GenerationStatus.RUNNING
                || !leaseToken.equals(reference.leaseToken())) {
            return Optional.empty();
        }
        ConversationEntity conversation = conversationRepository.findOwnedForUpdate(
                        reference.conversationId(), reference.ownerUserId())
                .orElseThrow(() -> new IllegalStateException("generation conversation is missing"));
        ChatGenerationEntity generation = lockRunningGeneration(generationId, leaseToken);
        if (generation == null) {
            return Optional.empty();
        }

        UUID messageId = UUID.randomUUID();
        ConversationMessageEntity message = new ConversationMessageEntity();
        message.setId(messageId);
        message.setConversationId(generation.getConversationId());
        message.setGenerationId(generation.getId());
        message.setSequenceNo(conversation.getNextMessageSequence());
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(completion.content());
        message.setTokenCount(toLong(completion.outputTokens()));
        message.setCreatedAt(completion.completedAt());
        conversation.setNextMessageSequence(conversation.getNextMessageSequence() + 1);
        conversation.setUpdatedAt(completion.completedAt());

        markTerminal(generation, GenerationStatus.COMPLETED, completion.completedAt(), null);
        generation.setInputTokens(toLong(completion.inputTokens()));
        generation.setOutputTokens(toLong(completion.outputTokens()));
        eventRepository.save(eventFactory.create(
                generation,
                GenerationEventType.COMPLETED,
                payloadCodec.completed(messageId, completion.finishReason(),
                        completion.inputTokens(), completion.outputTokens()),
                completion.completedAt(),
                completion.eventExpiresAt()
        ));
        messageRepository.save(message);
        generationRepository.flush();
        return Optional.of(mapper.toDomain(generation));
    }

    @Transactional
    public Optional<AssistantGeneration> fail(
            UUID generationId,
            UUID leaseToken,
            String errorCode,
            Instant failedAt,
            Instant eventExpiresAt
    ) {
        Objects.requireNonNull(generationId, "generationId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        GenerationTransitionTime.requireEventWindow(failedAt, eventExpiresAt);
        String safeErrorCode = safeErrorCode(errorCode);
        ChatGenerationEntity generation = lockRunningGeneration(generationId, leaseToken);
        if (generation == null) {
            return Optional.empty();
        }

        markTerminal(generation, GenerationStatus.FAILED, failedAt, safeErrorCode);
        eventRepository.save(eventFactory.create(
                generation, GenerationEventType.ERROR, payloadCodec.error(safeErrorCode),
                failedAt, eventExpiresAt));
        generationRepository.flush();
        return Optional.of(mapper.toDomain(generation));
    }

    private ChatGenerationEntity lockRunningGeneration(UUID generationId, UUID leaseToken) {
        ChatGenerationEntity generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        return generation != null
                && generation.getStatus() == GenerationStatus.RUNNING
                && leaseToken.equals(generation.getLeaseToken())
                ? generation
                : null;
    }

    private static void markTerminal(
            ChatGenerationEntity generation,
            GenerationStatus status,
            Instant occurredAt,
            String errorCode
    ) {
        generation.setStatus(status);
        generation.setActiveConversationId(null);
        generation.setErrorCode(errorCode);
        generation.setLeaseToken(null);
        generation.setLeaseExpiresAt(null);
        generation.setCompletedAt(occurredAt);
        generation.setUpdatedAt(occurredAt);
        generation.setProviderLatencyMs(GenerationTransitionTime.elapsedMillisIfStarted(
                generation.getStartedAt(), occurredAt));
        generation.setTotalLatencyMs(GenerationTransitionTime.elapsedMillis(
                generation.getQueuedAt(), occurredAt));
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String safeErrorCode(String value) {
        if (value == null || value.isBlank()) {
            return FALLBACK_ERROR_CODE;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return SAFE_ERROR_CODE.matcher(normalized).matches() ? normalized : FALLBACK_ERROR_CODE;
    }
}
