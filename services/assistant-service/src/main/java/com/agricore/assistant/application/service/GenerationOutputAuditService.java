package com.agricore.assistant.application.service;

import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantGeneration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
public class GenerationOutputAuditService {

    private static final String SAFETY_REASON_PREFIX = "AI_OUTPUT_";

    private final AssistantAuditRepository auditRepository;
    private final AssistantRetentionPolicy retentionPolicy;

    public GenerationOutputAuditService(
            AssistantAuditRepository auditRepository,
            AssistantRetentionPolicy retentionPolicy
    ) {
        this.auditRepository = auditRepository;
        this.retentionPolicy = retentionPolicy;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefusalIfNeeded(
            AssistantGeneration generation,
            String reasonCode,
            Instant occurredAt
    ) {
        Objects.requireNonNull(generation, "generation is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (reasonCode == null || !reasonCode.startsWith(SAFETY_REASON_PREFIX)) {
            return;
        }
        auditRepository.save(AssistantAuditEvent.outputDecision(
                generation.ownerUserId(),
                generation.farmId(),
                generation.conversationId(),
                generation.id(),
                outcome(reasonCode),
                reasonCode,
                occurredAt,
                occurredAt.plus(retentionPolicy.auditEventRetention())
        ));
    }

    private static String outcome(String reasonCode) {
        return "AI_OUTPUT_POLICY_UNAVAILABLE".equals(reasonCode) ? "FAILED" : "DENIED";
    }
}
