package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.AssistantRequestBudget;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.application.port.GenerationRepository;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import com.agricore.assistant.application.port.ToolEvidencePromptFormatter;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationSubmissionCoordinatorTest {

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatProvider chatProvider = mock(ChatProvider.class);
    private final ToolEvidenceCollector toolEvidenceCollector = mock(ToolEvidenceCollector.class);
    private final ToolEvidencePromptFormatter promptFormatter = mock(ToolEvidencePromptFormatter.class);
    private final GenerationSubmissionTransaction submissionTransaction = mock(GenerationSubmissionTransaction.class);
    private final AssistantRetentionPolicy retentionPolicy = mock(AssistantRetentionPolicy.class);
    private final AssistantRequestBudget requestBudget = mock(AssistantRequestBudget.class);
    private final ChatGenerationPolicy generationPolicy = generationPolicy();
    private final GenerationSubmissionCoordinator service = new GenerationSubmissionCoordinator(
            generationRepository,
            conversationRepository,
            retentionPolicy,
            chatProvider,
            generationPolicy,
            toolEvidenceCollector,
            submissionTransaction,
            mock(GenerationSubmissionAuditService.class),
            new GenerationSubmissionInputValidator(generationPolicy, promptFormatter),
            requestBudget,
            Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void returnsSameKeyActiveReplayFoundAfterInitialLookupMiss() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String prompt = "How is the crop?";
        String idempotencyKey = "same-key";
        AssistantConversation conversation = new AssistantConversation(
                conversationId, owner, "Assistant", ConversationContextType.ENTERPRISE, null,
                ConversationStatus.OPEN, List.of("FIELD_WORKER"), 0, 0,
                Instant.parse("2026-07-21T08:00:00Z"),
                Instant.parse("2026-07-21T08:00:00Z"), null, null
        );
        AssistantGeneration generation = mock(AssistantGeneration.class);
        when(generation.idempotencyKey()).thenReturn(idempotencyKey);
        when(generation.requestHash()).thenReturn(requestHash(conversationId, prompt));
        GenerationSubmissionResult active = new GenerationSubmissionResult(generation, null, true);

        when(promptFormatter.systemPolicy()).thenReturn("policy");
        when(promptFormatter.renderEvidence(any())).thenReturn("");
        when(conversationRepository.findOwned(conversationId, owner)).thenReturn(Optional.of(conversation));
        when(generationRepository.findIdempotent(
                conversationId, owner, idempotencyKey, requestHash(conversationId, prompt)))
                .thenReturn(Optional.empty());
        when(generationRepository.findActive(conversationId, owner)).thenReturn(Optional.of(active));

        GenerationSubmissionResult result = service.submit(
                new AssistantActor(owner, List.of("FIELD_WORKER")),
                conversationId,
                prompt,
                "  same-key  "
        );

        assertThat(result.generation()).isSameAs(generation);
        assertThat(result.deduplicated()).isTrue();
        verifyNoInteractions(chatProvider, toolEvidenceCollector, submissionTransaction, requestBudget);
    }

    @Test
    void reservesBudgetBeforeSubmittingANewGeneration() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AssistantActor actor = new AssistantActor(owner, List.of("FIELD_WORKER"));
        AssistantConversation conversation = new AssistantConversation(
                conversationId, owner, "Assistant", ConversationContextType.ENTERPRISE, null,
                ConversationStatus.OPEN, List.of("FIELD_WORKER"), 0, 0,
                Instant.parse("2026-07-21T08:00:00Z"),
                Instant.parse("2026-07-21T08:00:00Z"), null, null
        );
        GenerationSubmissionResult expected = new GenerationSubmissionResult(
                mock(AssistantGeneration.class), null, false);

        when(promptFormatter.systemPolicy()).thenReturn("policy");
        when(promptFormatter.renderEvidence(any())).thenReturn("");
        when(conversationRepository.findOwned(conversationId, owner)).thenReturn(Optional.of(conversation));
        when(generationRepository.findIdempotent(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(generationRepository.findActive(conversationId, owner)).thenReturn(Optional.empty());
        when(chatProvider.capabilities()).thenReturn(
                new ProviderCapabilities("openai", true, true, null));
        when(toolEvidenceCollector.collect(conversation))
                .thenReturn(ToolEvidenceCollection.skipped("TOOLS_DISABLED"));
        when(retentionPolicy.generationEventRetention()).thenReturn(Duration.ofHours(1));
        when(generationPolicy.model()).thenReturn("test-model");
        when(submissionTransaction.submit(any())).thenReturn(expected);

        GenerationSubmissionResult result = service.submit(
                actor, conversationId, "How is the crop?", "new-key", "203.0.113.10");

        assertThat(result).isSameAs(expected);
        verify(requestBudget).reserve(eq(actor), eq("203.0.113.10"), anyInt());
        verify(requestBudget).reserveAdditionalTokens(actor, "203.0.113.10", 0);
        verify(submissionTransaction).submit(any());
    }

    private static ChatGenerationPolicy generationPolicy() {
        ChatGenerationPolicy policy = mock(ChatGenerationPolicy.class);
        when(policy.maxInputCharacters()).thenReturn(1_024);
        return policy;
    }

    private static String requestHash(UUID conversationId, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (conversationId + "\n" + prompt).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
