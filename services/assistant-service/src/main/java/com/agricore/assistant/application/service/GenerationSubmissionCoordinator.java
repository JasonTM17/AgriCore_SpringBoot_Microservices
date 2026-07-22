package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.model.ToolCollectionOutcome;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class GenerationSubmissionCoordinator {

    private final GenerationRepository generationRepository;
    private final ConversationRepository conversationRepository;
    private final AssistantRetentionPolicy retentionPolicy;
    private final ChatProvider chatProvider;
    private final ChatGenerationPolicy generationPolicy;
    private final ToolEvidenceCollector toolEvidenceCollector;
    private final GenerationSubmissionTransaction submissionTransaction;
    private final GenerationSubmissionAuditService submissionAuditService;
    private final GenerationSubmissionInputValidator inputValidator;
    private final Clock clock;

    public GenerationSubmissionCoordinator(
            GenerationRepository generationRepository,
            ConversationRepository conversationRepository,
            AssistantRetentionPolicy retentionPolicy,
            ChatProvider chatProvider,
            ChatGenerationPolicy generationPolicy,
            ToolEvidenceCollector toolEvidenceCollector,
            GenerationSubmissionTransaction submissionTransaction,
            GenerationSubmissionAuditService submissionAuditService,
            GenerationSubmissionInputValidator inputValidator,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.conversationRepository = conversationRepository;
        this.retentionPolicy = retentionPolicy;
        this.chatProvider = chatProvider;
        this.generationPolicy = generationPolicy;
        this.toolEvidenceCollector = toolEvidenceCollector;
        this.submissionTransaction = submissionTransaction;
        this.submissionAuditService = submissionAuditService;
        this.inputValidator = inputValidator;
        this.clock = clock;
    }

    public GenerationSubmissionResult submit(
            AssistantActor actor,
            UUID conversationId,
            String prompt,
            String idempotencyKey
    ) {
        inputValidator.validatePrompt(prompt, ToolEvidenceSnapshot.empty());
        String normalizedKey = inputValidator.normalizeIdempotencyKey(idempotencyKey);
        AssistantConversation conversation = conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
        String requestHash = inputValidator.requestHash(conversation.id(), prompt);
        GenerationSubmissionResult existing = findIdempotent(
                actor, conversation, normalizedKey, requestHash);
        if (existing != null) {
            return existing;
        }
        if (conversation.status() != ConversationStatus.OPEN) {
            throw auditRejected(
                    actor, conversation, AssistantException.conversationNotOpen());
        }
        var active = generationRepository.findActive(conversation.id(), actor.subject());
        if (active.isPresent()) {
            try {
                return resolveActiveReplay(active.get(), normalizedKey, requestHash);
            } catch (AssistantException exception) {
                throw auditRejected(actor, conversation, exception);
            }
        }
        ProviderCapabilities capabilities = chatProvider.capabilities();
        if (!capabilities.available()) {
            throw auditRejected(
                    actor,
                    conversation,
                    AssistantException.providerUnavailable(capabilities.reasonCode())
            );
        }
        ToolEvidenceCollection collection = toolEvidenceCollector.collect(conversation);
        Instant now = clock.instant();
        if (collection.outcome() == ToolCollectionOutcome.DENIED) {
            submissionAuditService.recordToolDecisionWithoutGeneration(
                    actor, conversation, collection, now);
            throw AssistantException.toolContextUnavailable(collection.reasonCode());
        }
        validateEvidenceBudget(actor, conversation, prompt, collection, now);
        GenerationSubmissionCommand command = createCommand(
                actor, conversation, normalizedKey, requestHash, prompt, collection, capabilities, now);
        return submitCommand(actor, conversation, collection, command, now);
    }

    private GenerationSubmissionResult findIdempotent(
            AssistantActor actor,
            AssistantConversation conversation,
            String idempotencyKey,
            String requestHash
    ) {
        try {
            return generationRepository.findIdempotent(
                    conversation.id(), actor.subject(), idempotencyKey, requestHash).orElse(null);
        } catch (AssistantException exception) {
            throw auditRejected(actor, conversation, exception);
        }
    }

    private void validateEvidenceBudget(
            AssistantActor actor,
            AssistantConversation conversation,
            String prompt,
            ToolEvidenceCollection collection,
            Instant now
    ) {
        try {
            inputValidator.validatePrompt(prompt, collection.evidence());
        } catch (IllegalArgumentException exception) {
            submissionAuditService.recordDiscardedToolAttempt(
                    actor, conversation, collection, "INPUT_BUDGET_EXCEEDED", now);
            throw exception;
        }
    }

    private GenerationSubmissionCommand createCommand(
            AssistantActor actor,
            AssistantConversation conversation,
            String idempotencyKey,
            String requestHash,
            String prompt,
            ToolEvidenceCollection collection,
            ProviderCapabilities capabilities,
            Instant now
    ) {
        try {
            return new GenerationSubmissionCommand(
                    conversation.id(), actor.subject(), idempotencyKey, requestHash, prompt,
                    collection, capabilities.provider(), generationPolicy.model(), now, null,
                    now.plus(retentionPolicy.generationEventRetention())
            );
        } catch (IllegalArgumentException exception) {
            submissionAuditService.recordDiscardedToolAttempt(
                    actor, conversation, collection, "GENERATION_SUBMISSION_INVALID", now);
            throw exception;
        }
    }

    private GenerationSubmissionResult submitCommand(
            AssistantActor actor,
            AssistantConversation conversation,
            ToolEvidenceCollection collection,
            GenerationSubmissionCommand command,
            Instant now
    ) {
        try {
            GenerationSubmissionResult result = submissionTransaction.submit(command);
            if (result.deduplicated()) {
                submissionAuditService.recordDiscardedToolAttempt(
                        actor, conversation, collection, "IDEMPOTENT_REPLAY", now);
            }
            return result;
        } catch (AssistantException exception) {
            submissionAuditService.recordDiscardedToolAttempt(
                    actor, conversation, collection, exception.getCode(), now);
            throw exception;
        }
    }

    private static GenerationSubmissionResult resolveActiveReplay(
            GenerationSubmissionResult active,
            String idempotencyKey,
            String requestHash
    ) {
        AssistantGeneration generation = active.generation();
        if (!idempotencyKey.equals(generation.idempotencyKey())) {
            throw AssistantException.generationAlreadyActive();
        }
        if (!requestHash.equalsIgnoreCase(generation.requestHash())) {
            throw AssistantException.idempotencyKeyReused();
        }
        return new GenerationSubmissionResult(generation, active.userMessage(), true);
    }

    private AssistantException auditRejected(
            AssistantActor actor,
            AssistantConversation conversation,
            AssistantException exception
    ) {
        submissionAuditService.recordRejectedSubmission(
                actor,
                conversation,
                exception.getHttpStatus() >= 500 ? "FAILED" : "DENIED",
                exception.getCode(),
                clock.instant()
        );
        return exception;
    }
}
