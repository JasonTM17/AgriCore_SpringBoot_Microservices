package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.application.model.ProviderCapabilities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class GenerationApplicationService {

    private final GenerationRepository generationRepository;
    private final GenerationExecutionRepository executionRepository;
    private final ConversationRepository conversationRepository;
    private final AssistantAuditRepository auditRepository;
    private final AssistantRetentionPolicy retentionPolicy;
    private final ChatProvider chatProvider;
    private final ChatGenerationPolicy generationPolicy;
    private final GenerationWorkDispatcher workDispatcher;
    private final Clock clock;

    public GenerationApplicationService(
            GenerationRepository generationRepository,
            GenerationExecutionRepository executionRepository,
            ConversationRepository conversationRepository,
            AssistantAuditRepository auditRepository,
            AssistantRetentionPolicy retentionPolicy,
            ChatProvider chatProvider,
            ChatGenerationPolicy generationPolicy,
            GenerationWorkDispatcher workDispatcher,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.executionRepository = executionRepository;
        this.conversationRepository = conversationRepository;
        this.auditRepository = auditRepository;
        this.retentionPolicy = retentionPolicy;
        this.chatProvider = chatProvider;
        this.generationPolicy = generationPolicy;
        this.workDispatcher = workDispatcher;
        this.clock = clock;
    }

    @Transactional
    public GenerationSubmissionResult submit(
            AssistantActor actor,
            UUID conversationId,
            String prompt,
            String idempotencyKey
    ) {
        validatePrompt(prompt, generationPolicy.maxInputCharacters());
        validateIdempotencyKey(idempotencyKey);
        AssistantConversation conversation = conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
        String requestHash = requestHash(conversation.id(), prompt);
        var existing = generationRepository.findIdempotent(
                conversation.id(), actor.subject(), idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return existing.get();
        }
        ProviderCapabilities capabilities = chatProvider.capabilities();
        if (!capabilities.available()) {
            throw AssistantException.providerUnavailable(capabilities.reasonCode());
        }
        Instant now = clock.instant();
        GenerationSubmissionResult result = generationRepository.submit(new GenerationSubmissionCommand(
                conversation.id(),
                actor.subject(),
                idempotencyKey,
                requestHash,
                prompt,
                capabilities.provider(),
                generationPolicy.model(),
                now,
                null,
                now.plus(retentionPolicy.generationEventRetention())
        ));
        if (!result.deduplicated()) {
            auditRepository.save(AssistantAuditEvent.generationSuccess(
                    actor.subject(), actor.subject(), conversation.farmId(), conversation.id(),
                    result.generation().id(), "GENERATION_SUBMITTED", now,
                    now.plus(retentionPolicy.auditEventRetention())
            ));
            workDispatcher.dispatchAfterCommit(result.generation().id());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AssistantGeneration get(AssistantActor actor, UUID conversationId, UUID generationId) {
        conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
        return generationRepository.findOwned(generationId, conversationId, actor.subject())
                .orElseThrow(AssistantException::generationNotFound);
    }

    @Transactional
    public AssistantGeneration cancel(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId
    ) {
        AssistantConversation conversation = conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
        Instant now = clock.instant();
        var result = executionRepository.requestCancellation(
                generationId,
                conversation.id(),
                actor.subject(),
                now,
                now.plus(retentionPolicy.generationEventRetention())
        );
        if (result.changed()) {
            auditRepository.save(AssistantAuditEvent.generationSuccess(
                    actor.subject(), actor.subject(), conversation.farmId(), conversation.id(),
                    generationId, "GENERATION_CANCELLATION_REQUESTED", now,
                    now.plus(retentionPolicy.auditEventRetention())
            ));
        }
        if (result.workerCancellationRequired()) {
            workDispatcher.cancelAfterCommit(generationId);
        }
        return result.generation();
    }

    @Transactional(readOnly = true)
    public List<AssistantGenerationEvent> events(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long afterSequence,
            int limit
    ) {
        return eventBatch(actor, conversationId, generationId, afterSequence, limit).events();
    }

    @Transactional(readOnly = true)
    public GenerationEventReplayBatch eventBatch(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long afterSequence,
            int limit
    ) {
        conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
        AssistantGeneration generation = generationRepository.findOwned(
                        generationId, conversationId, actor.subject())
                .orElseThrow(AssistantException::generationNotFound);
        List<AssistantGenerationEvent> events = generationRepository.findEventsOwned(
                generationId, conversationId, actor.subject(), afterSequence, limit, clock.instant());
        return GenerationEventReplayBatch.validated(
                events,
                generation.nextEventSequence(),
                generation.terminal(),
                afterSequence
        );
    }

    private static String requestHash(UUID conversationId, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((conversationId + "\n" + prompt)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void validatePrompt(String prompt, int maximumLength) {
        if (prompt == null || prompt.isBlank() || prompt.strip().length() > maximumLength) {
            throw new IllegalArgumentException("Invalid generation prompt");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.strip().length() > 128) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
    }
}
