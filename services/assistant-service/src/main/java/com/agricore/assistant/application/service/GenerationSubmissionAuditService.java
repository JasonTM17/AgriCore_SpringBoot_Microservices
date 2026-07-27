package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.ToolCollectionOutcome;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantGeneration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class GenerationSubmissionAuditService {

    private static final String REASON_PATTERN = "[A-Z][A-Z0-9_]{0,63}";

    private final AssistantAuditRepository auditRepository;
    private final AssistantRetentionPolicy retentionPolicy;

    public GenerationSubmissionAuditService(
            AssistantAuditRepository auditRepository,
            AssistantRetentionPolicy retentionPolicy
    ) {
        this.auditRepository = auditRepository;
        this.retentionPolicy = retentionPolicy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordToolDecisionWithoutGeneration(
            AssistantActor actor,
            AssistantConversation conversation,
            ToolEvidenceCollection collection,
            Instant now
    ) {
        requireContext(actor, conversation, collection, now);
        auditRepository.save(AssistantAuditEvent.toolDecision(
                actor.subject(), conversation.farmId(), conversation.id(), null,
                auditOutcome(collection), collection.reasonCode(), collection.auditMetadata(),
                now, now.plus(retentionPolicy.auditEventRetention())
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDiscardedToolAttempt(
            AssistantActor actor,
            AssistantConversation conversation,
            ToolEvidenceCollection collection,
            String reasonCode,
            Instant now
    ) {
        requireContext(actor, conversation, collection, now);
        String safeReason = requireReasonCode(reasonCode);
        auditRepository.save(AssistantAuditEvent.toolAttempt(
                actor.subject(), conversation.farmId(), conversation.id(),
                auditOutcome(collection), safeReason, collection.auditMetadata(),
                now, now.plus(retentionPolicy.auditEventRetention())
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordToolDecisionForGeneration(
            UUID actorSubject,
            AssistantGeneration generation,
            ToolEvidenceCollection collection,
            Instant now
    ) {
        Objects.requireNonNull(actorSubject, "assistant actor subject is required");
        Objects.requireNonNull(generation, "assistant generation is required");
        Objects.requireNonNull(collection, "tool evidence collection is required");
        Objects.requireNonNull(now, "tool decision timestamp is required");
        auditRepository.save(AssistantAuditEvent.toolDecision(
                actorSubject, generation.farmId(), generation.conversationId(), generation.id(),
                auditOutcome(collection), collection.reasonCode(), collection.auditMetadata(),
                now, now.plus(retentionPolicy.auditEventRetention())
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejectedSubmission(
            AssistantActor actor,
            AssistantConversation conversation,
            String outcome,
            String reasonCode,
            Instant now
    ) {
        Objects.requireNonNull(actor, "assistant actor is required");
        Objects.requireNonNull(conversation, "assistant conversation is required");
        Objects.requireNonNull(now, "submission rejection timestamp is required");
        if (!"DENIED".equals(outcome) && !"FAILED".equals(outcome)) {
            throw new IllegalArgumentException("submission rejection outcome is invalid");
        }
        String safeReason = requireReasonCode(reasonCode);
        auditRepository.save(AssistantAuditEvent.submissionRejected(
                actor.subject(), conversation.farmId(), conversation.id(), outcome, safeReason,
                now, now.plus(retentionPolicy.auditEventRetention())
        ));
    }

    private static void requireContext(
            AssistantActor actor,
            AssistantConversation conversation,
            ToolEvidenceCollection collection,
            Instant now
    ) {
        Objects.requireNonNull(actor, "assistant actor is required");
        Objects.requireNonNull(conversation, "assistant conversation is required");
        Objects.requireNonNull(collection, "tool evidence collection is required");
        Objects.requireNonNull(now, "tool decision timestamp is required");
    }

    private static String auditOutcome(ToolEvidenceCollection collection) {
        if (collection.outcome() == ToolCollectionOutcome.COLLECTED
                || collection.outcome() == ToolCollectionOutcome.PARTIAL
                || collection.outcome() == ToolCollectionOutcome.SKIPPED) {
            return "SUCCESS";
        }
        return collection.outcome() == ToolCollectionOutcome.DENIED
                && "TOOL_SCOPE_UNAVAILABLE".equals(collection.reasonCode())
                ? "DENIED"
                : "FAILED";
    }

    private static String requireReasonCode(String reasonCode) {
        String normalized = reasonCode == null ? "" : reasonCode.strip();
        if (!normalized.matches(REASON_PATTERN)) {
            throw new IllegalArgumentException("audit reason code is invalid");
        }
        return normalized;
    }
}
