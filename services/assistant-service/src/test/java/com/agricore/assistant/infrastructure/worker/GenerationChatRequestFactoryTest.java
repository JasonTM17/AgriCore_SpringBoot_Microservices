package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationChatRequestFactoryTest {

    private final ChatGenerationPolicy policy = mock(ChatGenerationPolicy.class);
    private final AssistantGeneration generation = mock(AssistantGeneration.class);
    private final GenerationChatRequestFactory factory = new GenerationChatRequestFactory(policy);

    @BeforeEach
    void configurePolicy() {
        when(policy.maxInputCharacters()).thenReturn(12);
        when(policy.maxOutputTokens()).thenReturn(256);
        when(policy.temperature()).thenReturn(0.3);
        when(generation.model()).thenReturn("persisted-model");
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
        assertThat(request.turns()).hasSize(1);
        assertThat(request.turns().getFirst().role()).isEqualTo(ChatTurnRole.USER);
        assertThat(request.turns().getFirst().content()).isEqualTo("new-user");
    }

    @Test
    void rejectsCorruptOrOversizedLatestContextWithoutCallingAProvider() {
        assertThatThrownBy(() -> factory.create(new GenerationExecutionContext(generation, List.of(
                message(0, MessageRole.ASSISTANT, "not-a-user")
        )))).isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo("GENERATION_CONTEXT_INVALID");

        when(policy.maxInputCharacters()).thenReturn(4);
        assertThatThrownBy(() -> factory.create(new GenerationExecutionContext(generation, List.of(
                message(0, MessageRole.USER, "too-long")
        )))).isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo("AI_INPUT_TOO_LARGE");
    }

    private AssistantMessage message(long sequence, MessageRole role, String content) {
        return new AssistantMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sequence,
                role, content, null, Instant.EPOCH);
    }
}
