package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class GenerationSubmissionTransaction {

    private final GenerationRepository generationRepository;
    private final AssistantAuditRepository auditRepository;
    private final AssistantRetentionPolicy retentionPolicy;
    private final GenerationWorkDispatcher workDispatcher;
    private final GenerationSubmissionAuditService submissionAuditService;

    public GenerationSubmissionTransaction(
            GenerationRepository generationRepository,
            AssistantAuditRepository auditRepository,
            AssistantRetentionPolicy retentionPolicy,
            GenerationWorkDispatcher workDispatcher,
            GenerationSubmissionAuditService submissionAuditService
    ) {
        this.generationRepository = generationRepository;
        this.auditRepository = auditRepository;
        this.retentionPolicy = retentionPolicy;
        this.workDispatcher = workDispatcher;
        this.submissionAuditService = submissionAuditService;
    }

    @Transactional
    public GenerationSubmissionResult submit(GenerationSubmissionCommand command) {
        Objects.requireNonNull(command, "generation submission command is required");
        GenerationSubmissionResult result = generationRepository.submit(command);
        if (result.deduplicated()) {
            return result;
        }
        var generation = result.generation();
        submissionAuditService.recordToolDecisionForGeneration(
                command.ownerUserId(), generation, command.toolCollection(), command.now());
        auditRepository.save(AssistantAuditEvent.generationSuccess(
                command.ownerUserId(), command.ownerUserId(), generation.farmId(),
                generation.conversationId(), generation.id(), "GENERATION_SUBMITTED",
                command.now(), command.now().plus(retentionPolicy.auditEventRetention())
        ));
        workDispatcher.dispatchAfterCommit(generation.id());
        return result;
    }
}
