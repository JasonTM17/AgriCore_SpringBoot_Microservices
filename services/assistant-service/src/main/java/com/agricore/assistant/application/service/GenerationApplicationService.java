package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantGeneration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class GenerationApplicationService {

    private final GenerationRepository generationRepository;
    private final GenerationExecutionRepository executionRepository;
    private final ConversationRepository conversationRepository;
    private final AssistantAuditRepository auditRepository;
    private final AssistantRetentionPolicy retentionPolicy;
    private final GenerationWorkDispatcher workDispatcher;
    private final GenerationSubmissionCoordinator submissionCoordinator;
    private final Clock clock;

    public GenerationApplicationService(
            GenerationRepository generationRepository,
            GenerationExecutionRepository executionRepository,
            ConversationRepository conversationRepository,
            AssistantAuditRepository auditRepository,
            AssistantRetentionPolicy retentionPolicy,
            GenerationWorkDispatcher workDispatcher,
            GenerationSubmissionCoordinator submissionCoordinator,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.executionRepository = executionRepository;
        this.conversationRepository = conversationRepository;
        this.auditRepository = auditRepository;
        this.retentionPolicy = retentionPolicy;
        this.workDispatcher = workDispatcher;
        this.submissionCoordinator = submissionCoordinator;
        this.clock = clock;
    }

    public GenerationSubmissionResult submit(
            AssistantActor actor,
            UUID conversationId,
            String prompt,
            String idempotencyKey
    ) {
        return submissionCoordinator.submit(actor, conversationId, prompt, idempotencyKey);
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

}
