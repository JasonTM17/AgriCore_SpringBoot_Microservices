package com.agricore.assistant.application;

import com.agricore.assistant.api.response.AssistantDtos.CapabilitiesResponse;
import com.agricore.assistant.api.response.AssistantDtos.ConversationResponse;
import com.agricore.assistant.api.response.AssistantDtos.MessageResponse;
import com.agricore.assistant.api.response.AssistantDtos.StartGenerationResponse;
import com.agricore.assistant.domain.AssistantException;
import com.agricore.assistant.infrastructure.config.AssistantProperties;
import com.agricore.assistant.infrastructure.persistence.AssistantAuditJpaRepository;
import com.agricore.assistant.infrastructure.persistence.ChatGenerationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.ConversationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.ConversationMessageJpaRepository;
import com.agricore.assistant.infrastructure.persistence.GenerationEventJpaRepository;
import com.agricore.assistant.infrastructure.persistence.entity.AssistantAuditEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ChatGenerationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationEntity;
import com.agricore.assistant.infrastructure.persistence.entity.ConversationMessageEntity;
import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import com.agricore.assistant.infrastructure.provider.ChatProvider;
import com.agricore.assistant.infrastructure.provider.ChatProviderRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AssistantApplicationService {

    private static final List<String> ALLOWED_TOOLS = List.of(
            "list_farms", "get_farm", "list_crop_cycles", "get_inventory_item", "get_public_trace"
    );

    private final ConversationJpaRepository conversationRepository;
    private final ConversationMessageJpaRepository messageRepository;
    private final ChatGenerationJpaRepository generationRepository;
    private final GenerationEventJpaRepository eventRepository;
    private final AssistantAuditJpaRepository auditRepository;
    private final AssistantProperties properties;
    private final ChatProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    public AssistantApplicationService(
            ConversationJpaRepository conversationRepository,
            ConversationMessageJpaRepository messageRepository,
            ChatGenerationJpaRepository generationRepository,
            GenerationEventJpaRepository eventRepository,
            AssistantAuditJpaRepository auditRepository,
            AssistantProperties properties,
            ChatProviderRegistry providerRegistry,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public CapabilitiesResponse capabilities() {
        ChatProvider provider = providerRegistry.active();
        boolean available = properties.generationAvailable() && provider.available();
        return new CapabilitiesResponse(
                properties.normalizedProvider(),
                available,
                true,
                ALLOWED_TOOLS,
                available ? null : "No LLM provider key configured or provider=none"
        );
    }

    @Transactional
    public ConversationResponse createConversation(UUID ownerId, List<String> roles, String title, UUID farmId) {
        Instant now = Instant.now();
        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerId);
        entity.setTitle(title == null || title.isBlank() ? "Hội thoại mới" : title.trim());
        entity.setFarmId(farmId);
        entity.setStatus("OPEN");
        entity.setRoleSnapshot(String.join(",", roles == null ? List.of() : roles));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        conversationRepository.save(entity);
        audit(ownerId, entity.getId(), null, "CONVERSATION_CREATED", "title=" + entity.getTitle());
        return toConversation(entity);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(UUID ownerId) {
        return conversationRepository.findByOwnerUserIdAndArchivedAtIsNullOrderByUpdatedAtDesc(ownerId)
                .stream()
                .map(this::toConversation)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(UUID ownerId, UUID conversationId) {
        requireOwnedConversation(conversationId, ownerId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Transactional
    public StartGenerationResponse startGeneration(
            UUID ownerId,
            List<String> roles,
            UUID conversationId,
            String content,
            String idempotencyKey
    ) {
        if (content == null || content.isBlank()) {
            throw new AssistantException("VALIDATION_FAILED", "content is required", 400);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AssistantException("VALIDATION_FAILED", "idempotencyKey is required", 400);
        }

        ConversationEntity conversation = requireOwnedConversation(conversationId, ownerId);

        var existing = generationRepository.findByOwnerUserIdAndConversationIdAndIdempotencyKey(
                ownerId, conversationId, idempotencyKey.trim());
        if (existing.isPresent()) {
            return new StartGenerationResponse(existing.get().getId(), existing.get().getStatus());
        }

        if (!properties.generationAvailable() || !providerRegistry.active().available()) {
            throw new AssistantException(
                    "PROVIDER_UNAVAILABLE",
                    "Chat generation is unavailable because no provider is configured",
                    503
            );
        }

        long active = generationRepository.countByConversationIdAndStatusIn(
                conversationId, List.of("QUEUED", "RUNNING"));
        if (active > 0) {
            throw new AssistantException(
                    "GENERATION_IN_PROGRESS",
                    "Another generation is already active for this conversation",
                    409
            );
        }

        Instant now = Instant.now();
        ConversationMessageEntity userMessage = new ConversationMessageEntity();
        userMessage.setId(UUID.randomUUID());
        userMessage.setConversationId(conversationId);
        userMessage.setRole("USER");
        userMessage.setContent(content.trim());
        userMessage.setCreatedAt(now);
        messageRepository.save(userMessage);

        ChatGenerationEntity generation = new ChatGenerationEntity();
        generation.setId(UUID.randomUUID());
        generation.setConversationId(conversationId);
        generation.setOwnerUserId(ownerId);
        generation.setIdempotencyKey(idempotencyKey.trim());
        generation.setStatus("QUEUED");
        generation.setUserMessageId(userMessage.getId());
        generation.setCreatedAt(now);
        generation.setUpdatedAt(now);
        generationRepository.save(generation);

        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        appendEvent(generation.getId(), "status", Map.of("type", "status", "status", "QUEUED"));
        audit(ownerId, conversationId, generation.getId(), "GENERATION_STARTED", "roles=" + String.join(",", roles));

        UUID generationId = generation.getId();
        // Run only after the enclosing transaction commits so the worker can load rows.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    workers.submit(() -> runGeneration(generationId));
                }
            });
        } else {
            workers.submit(() -> runGeneration(generationId));
        }

        return new StartGenerationResponse(generationId, "QUEUED");
    }

    @Transactional(readOnly = true)
    public List<GenerationEventEntity> eventsAfter(UUID ownerId, UUID generationId, long afterSequence) {
        ChatGenerationEntity generation = generationRepository.findByIdAndOwnerUserId(generationId, ownerId)
                .orElseThrow(() -> new AssistantException("NOT_FOUND", "Generation not found", 404));
        requireOwnedConversation(generation.getConversationId(), ownerId);
        return eventRepository.findByGenerationIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                generationId, afterSequence);
    }

    @Transactional(readOnly = true)
    public ChatGenerationEntity requireOwnedGeneration(UUID ownerId, UUID generationId) {
        return generationRepository.findByIdAndOwnerUserId(generationId, ownerId)
                .orElseThrow(() -> new AssistantException("NOT_FOUND", "Generation not found", 404));
    }

    private void runGeneration(UUID generationId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ChatGenerationEntity generation = generationRepository.findById(generationId).orElse(null);
                if (generation == null) {
                    return;
                }
                generation.setStatus("RUNNING");
                generation.setUpdatedAt(Instant.now());
                generationRepository.save(generation);
                appendEvent(generationId, "status", Map.of("type", "status", "status", "RUNNING"));
            });

            ChatGenerationEntity generation = generationRepository.findById(generationId).orElseThrow();
            List<ConversationMessageEntity> history =
                    messageRepository.findByConversationIdOrderByCreatedAtAsc(generation.getConversationId());
            List<ChatProvider.ChatMessage> providerHistory = new ArrayList<>();
            String userPrompt = "";
            for (ConversationMessageEntity message : history) {
                if (message.getId().equals(generation.getUserMessageId())) {
                    userPrompt = message.getContent();
                } else {
                    providerHistory.add(new ChatProvider.ChatMessage(message.getRole(), message.getContent()));
                }
            }

            ChatProvider provider = providerRegistry.active();
            StringBuilder full = new StringBuilder();
            String answer = provider.generate(providerHistory, userPrompt, delta -> {
                full.append(delta);
                transactionTemplate.executeWithoutResult(status ->
                        appendEvent(generationId, "delta", Map.of("type", "delta", "delta", delta)));
            });

            transactionTemplate.executeWithoutResult(status -> {
                ChatGenerationEntity gen = generationRepository.findById(generationId).orElseThrow();
                Instant now = Instant.now();
                ConversationMessageEntity assistantMessage = new ConversationMessageEntity();
                assistantMessage.setId(UUID.randomUUID());
                assistantMessage.setConversationId(gen.getConversationId());
                assistantMessage.setRole("ASSISTANT");
                assistantMessage.setContent(answer == null || answer.isBlank() ? full.toString() : answer);
                assistantMessage.setGenerationId(generationId);
                assistantMessage.setCreatedAt(now);
                messageRepository.save(assistantMessage);

                gen.setStatus("COMPLETED");
                gen.setAssistantMessageId(assistantMessage.getId());
                gen.setUpdatedAt(now);
                gen.setCompletedAt(now);
                generationRepository.save(gen);

                conversationRepository.findById(gen.getConversationId()).ifPresent(c -> {
                    c.setUpdatedAt(now);
                    conversationRepository.save(c);
                });

                appendEvent(generationId, "completed", Map.of(
                        "type", "completed",
                        "status", "COMPLETED",
                        "messageId", assistantMessage.getId().toString()
                ));
                audit(gen.getOwnerUserId(), gen.getConversationId(), generationId, "GENERATION_COMPLETED", null);
            });
        } catch (Exception ex) {
            transactionTemplate.executeWithoutResult(status -> {
                generationRepository.findById(generationId).ifPresent(gen -> {
                    Instant now = Instant.now();
                    gen.setStatus("FAILED");
                    gen.setErrorCode("GENERATION_FAILED");
                    gen.setErrorMessage(ex.getMessage() == null ? "generation failed" : ex.getMessage());
                    gen.setUpdatedAt(now);
                    gen.setCompletedAt(now);
                    generationRepository.save(gen);
                    appendEvent(generationId, "error", Map.of(
                            "type", "error",
                            "message", gen.getErrorMessage()
                    ));
                    audit(gen.getOwnerUserId(), gen.getConversationId(), generationId, "GENERATION_FAILED",
                            gen.getErrorCode());
                });
            });
        }
    }

    private ConversationEntity requireOwnedConversation(UUID conversationId, UUID ownerId) {
        return conversationRepository.findByIdAndOwnerUserId(conversationId, ownerId)
                .orElseThrow(() -> new AssistantException("NOT_FOUND", "Conversation not found", 404));
    }

    private void appendEvent(UUID generationId, String eventType, Map<String, Object> payload) {
        long next = eventRepository.maxSequence(generationId) + 1;
        GenerationEventEntity event = new GenerationEventEntity();
        event.setId(UUID.randomUUID());
        event.setGenerationId(generationId);
        event.setSequenceNo(next);
        event.setEventType(eventType);
        event.setPayload(writeJson(payload));
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }

    private void audit(UUID ownerId, UUID conversationId, UUID generationId, String action, String detail) {
        AssistantAuditEntity audit = new AssistantAuditEntity();
        audit.setId(UUID.randomUUID());
        audit.setOwnerUserId(ownerId);
        audit.setConversationId(conversationId);
        audit.setGenerationId(generationId);
        audit.setAction(action);
        audit.setDetail(detail);
        audit.setCreatedAt(Instant.now());
        auditRepository.save(audit);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"payload_serialization_failed\"}";
        }
    }

    private ConversationResponse toConversation(ConversationEntity entity) {
        return new ConversationResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getFarmId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MessageResponse toMessage(ConversationMessageEntity entity) {
        return new MessageResponse(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getGenerationId()
        );
    }
}
