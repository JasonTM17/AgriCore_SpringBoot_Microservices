package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationSubmissionCommand;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantGeneration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationSubmissionTransactionTest {

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final AssistantAuditRepository auditRepository = mock(AssistantAuditRepository.class);
    private final AssistantRetentionPolicy retentionPolicy = mock(AssistantRetentionPolicy.class);
    private final GenerationWorkDispatcher dispatcher = mock(GenerationWorkDispatcher.class);
    private final GenerationSubmissionAuditService submissionAuditService = new GenerationSubmissionAuditService(
            auditRepository, retentionPolicy
    );
    private final GenerationSubmissionTransaction transaction = new GenerationSubmissionTransaction(
            generationRepository, auditRepository, retentionPolicy, dispatcher, submissionAuditService
    );

    @Test
    void auditsSubmissionAndDispatchesOnlyForANewGeneration() {
        GenerationSubmissionCommand command = command();
        AssistantGeneration generation = generation(command);
        when(retentionPolicy.auditEventRetention()).thenReturn(Duration.ofDays(30));
        when(generationRepository.submit(command)).thenReturn(new GenerationSubmissionResult(
                generation, null, false
        ));
        transaction.submit(command);

        ArgumentCaptor<AssistantAuditEvent> audit = ArgumentCaptor.forClass(AssistantAuditEvent.class);
        verify(auditRepository, times(2)).save(audit.capture());
        assertThat(audit.getAllValues()).extracting(AssistantAuditEvent::action)
                .containsExactly("TOOL_EVIDENCE_DECISION", "GENERATION_SUBMITTED");
        assertThat(audit.getAllValues().getFirst().generationId()).isEqualTo(generation.id());
        verify(dispatcher).dispatchAfterCommit(generation.id());
    }

    @Test
    void doesNotDuplicateAuditOrDispatchForAnIdempotentReplay() {
        GenerationSubmissionCommand command = command();
        AssistantGeneration generation = generation(command);
        when(generationRepository.submit(command)).thenReturn(new GenerationSubmissionResult(
                generation, null, true
        ));

        transaction.submit(command);

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(dispatcher, never()).dispatchAfterCommit(org.mockito.ArgumentMatchers.any());
    }

    private GenerationSubmissionCommand command() {
        Instant now = Instant.parse("2026-07-21T08:00:00Z");
        return new GenerationSubmissionCommand(
                UUID.randomUUID(), UUID.randomUUID(), "request-1", "a".repeat(64),
                "How is the crop?", ToolEvidenceCollection.skipped("TOOLS_DISABLED"), "none", null,
                now, null, now.plusSeconds(60)
        );
    }

    private AssistantGeneration generation(GenerationSubmissionCommand command) {
        AssistantGeneration generation = mock(AssistantGeneration.class);
        when(generation.id()).thenReturn(UUID.randomUUID());
        when(generation.conversationId()).thenReturn(command.conversationId());
        when(generation.ownerUserId()).thenReturn(command.ownerUserId());
        when(generation.farmId()).thenReturn(UUID.randomUUID());
        return generation;
    }
}
