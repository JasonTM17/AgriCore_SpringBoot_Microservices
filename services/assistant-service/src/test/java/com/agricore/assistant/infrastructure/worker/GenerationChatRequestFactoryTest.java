package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ToolEvidencePromptFormatter;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationChatRequestFactoryTest {

    private final ChatGenerationPolicy policy = mock(ChatGenerationPolicy.class);
    private final AssistantGeneration generation = mock(AssistantGeneration.class);
    private final ToolEvidencePromptFormatter evidenceRenderer = mock(ToolEvidencePromptFormatter.class);
    private final GenerationChatRequestFactory factory = new GenerationChatRequestFactory(policy, evidenceRenderer);

    @BeforeEach
    void configurePolicy() {
        when(policy.maxInputCharacters()).thenReturn(18);
        when(policy.maxOutputTokens()).thenReturn(256);
        when(policy.temperature()).thenReturn(0.3);
        when(generation.model()).thenReturn("persisted-model");
        when(generation.toolEvidence()).thenReturn(ToolEvidenceSnapshot.empty());
        when(evidenceRenderer.systemPolicy()).thenReturn("policy");
        when(evidenceRenderer.renderEvidence(ToolEvidenceSnapshot.empty())).thenReturn("");
    }

    @Test
    void selectsAContiguousNewestSuffixWithinTheInputBudget() {
        var request = factory.create(new GenerationExecutionContext(generation, List.of(
                message(0, MessageRole.USER, "old-user"),
                message(1, MessageRole.ASSISTANT, "old-answer"),
                message(2, MessageRole.USER, "new-user")
        )));

        assertThat(request.model()).isEqualTo("persisted-model");
        assertThat(request.maxOutputTokens()).isEqualTo(256);
        assertThat(request.temperature()).isEqualTo(0.3);
        assertThat(request.turns()).hasSize(2);
        assertThat(request.turns().getFirst().role()).isEqualTo(ChatTurnRole.SYSTEM);
        assertThat(request.turns().getFirst().content()).isEqualTo("policy");
        assertThat(request.turns().getLast().role()).isEqualTo(ChatTurnRole.USER);
        assertThat(request.turns().getLast().content()).isEqualTo("new-user");
    }

    @Test
    void rejectsCorruptOrOversizedLatestContextWithoutCallingAProvider() {
        assertThatThrownBy(() -> factory.create(new GenerationExecutionContext(generation, List.of(
                message(0, MessageRole.ASSISTANT, "not-a-user")
        )))).isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo("GENERATION_CONTEXT_INVALID");

        when(policy.maxInputCharacters()).thenReturn(10);
        assertThatThrownBy(() -> factory.create(new GenerationExecutionContext(generation, List.of(
                message(0, MessageRole.USER, "too-long")
        )))).isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo("AI_INPUT_TOO_LARGE");
    }

    @Test
    void keepsUntrustedEvidenceBelowSystemPriorityAndReservesProviderTurnCapacity() {
        ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(new ToolFact(
                "FARM-1",
                ToolSource.FARM,
                Map.of("name", "untrusted farm")
        )));
        when(generation.toolEvidence()).thenReturn(evidence);
        when(evidenceRenderer.renderEvidence(evidence)).thenReturn("untrusted evidence");
        when(policy.maxInputCharacters()).thenReturn(1_000);
        List<AssistantMessage> messages = LongStream.range(0, 100)
                .mapToObj(index -> message(index, MessageRole.USER, "m" + index))
                .toList();

        var request = factory.create(new GenerationExecutionContext(generation, messages));

        assertThat(request.turns()).hasSize(100);
        assertThat(request.turns().getFirst().role()).isEqualTo(ChatTurnRole.SYSTEM);
        assertThat(request.turns().getFirst().content()).doesNotContain("untrusted evidence");
        assertThat(request.turns().get(1).role()).isEqualTo(ChatTurnRole.USER);
        assertThat(request.turns().get(1).content()).isEqualTo("untrusted evidence");
        assertThat(request.turns().getLast().content()).isEqualTo("m99");
    }

    private AssistantMessage message(long sequence, MessageRole role, String content) {
        return new AssistantMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sequence,
                role, content, null, Instant.EPOCH);
    }
}
