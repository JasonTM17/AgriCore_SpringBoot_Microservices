package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationSubmissionAuditServiceTest {

    private final AssistantAuditRepository auditRepository = mock(AssistantAuditRepository.class);
    private final AssistantRetentionPolicy retentionPolicy = mock(AssistantRetentionPolicy.class);
    private final GenerationSubmissionAuditService service = new GenerationSubmissionAuditService(
            auditRepository, retentionPolicy
    );

    @Test
    void recordsDeniedDecisionWithoutGenerationOrSensitiveValues() {
        UUID owner = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        AssistantConversation conversation = conversation(owner, farmId);
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        when(retentionPolicy.auditEventRetention()).thenReturn(Duration.ofDays(30));

        service.recordToolDecisionWithoutGeneration(
                new AssistantActor(owner, List.of("FARM_MANAGER")),
                conversation,
                ToolEvidenceCollection.denied("TOOL_SCOPE_UNAVAILABLE", 7),
                now
        );

        AssistantAuditEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("TOOL_EVIDENCE_DECISION");
        assertThat(event.outcome()).isEqualTo("DENIED");
        assertThat(event.reasonCode()).isEqualTo("TOOL_SCOPE_UNAVAILABLE");
        assertThat(event.generationId()).isNull();
        assertThat(event.metadata()).contains("\"latencyMs\":7")
                .doesNotContain(owner.toString(), farmId.toString(), conversation.id().toString());
    }

    @Test
    void recordsAuthorizationInfrastructureFailureAsFailedRatherThanDenied() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        when(retentionPolicy.auditEventRetention()).thenReturn(Duration.ofDays(30));

        service.recordToolDecisionWithoutGeneration(
                new AssistantActor(owner, List.of("FIELD_WORKER")),
                conversation(owner, null),
                ToolEvidenceCollection.denied("TOOL_AUTHORIZATION_UNAVAILABLE", 7),
                now
        );

        AssistantAuditEvent event = capturedEvent();
        assertThat(event.outcome()).isEqualTo("FAILED");
        assertThat(event.reasonCode()).isEqualTo("TOOL_AUTHORIZATION_UNAVAILABLE");
    }

    @Test
    void distinguishesDiscardedCollectionFromGenerationDecision() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        when(retentionPolicy.auditEventRetention()).thenReturn(Duration.ofDays(30));

        service.recordDiscardedToolAttempt(
                new AssistantActor(owner, List.of("FIELD_WORKER")),
                conversation(owner, null),
                ToolEvidenceCollection.skipped("TOOLS_DISABLED"),
                "IDEMPOTENT_REPLAY",
                now
        );

        AssistantAuditEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("TOOL_EVIDENCE_ATTEMPT");
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.reasonCode()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(event.generationId()).isNull();
    }

    @Test
    void recordsPreflightSubmissionRejectionWithoutInventingToolActivity() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        when(retentionPolicy.auditEventRetention()).thenReturn(Duration.ofDays(30));

        service.recordRejectedSubmission(
                new AssistantActor(owner, List.of("FIELD_WORKER")),
                conversation(owner, null),
                "DENIED",
                "GENERATION_ALREADY_ACTIVE",
                now
        );

        AssistantAuditEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo("GENERATION_SUBMISSION_REJECTED");
        assertThat(event.outcome()).isEqualTo("DENIED");
        assertThat(event.reasonCode()).isEqualTo("GENERATION_ALREADY_ACTIVE");
        assertThat(event.metadata()).isNull();
    }

    private AssistantAuditEvent capturedEvent() {
        ArgumentCaptor<AssistantAuditEvent> event = ArgumentCaptor.forClass(AssistantAuditEvent.class);
        verify(auditRepository).save(event.capture());
        return event.getValue();
    }

    private AssistantConversation conversation(UUID owner, UUID farmId) {
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        return new AssistantConversation(
                UUID.randomUUID(), owner, "Assistant",
                farmId == null ? ConversationContextType.ENTERPRISE : ConversationContextType.FARM,
                farmId, ConversationStatus.OPEN, List.of("FIELD_WORKER"), 0, 0,
                now, now, null, null
        );
    }
}
