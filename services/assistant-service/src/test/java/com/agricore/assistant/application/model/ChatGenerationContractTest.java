package com.agricore.assistant.application.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatGenerationContractTest {

    @Test
    void normalizesAndDefensivelyCopiesRequestValues() {
        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn(ChatTurnRole.USER, "  How are the crops?  "));

        ChatGenerationRequest request = new ChatGenerationRequest(turns, "  gpt-4.1-mini  ", 512, 0.3);
        turns.clear();

        assertThat(request.turns()).containsExactly(
                new ChatTurn(ChatTurnRole.USER, "How are the crops?")
        );
        assertThat(request.model()).isEqualTo("gpt-4.1-mini");
        assertThatThrownBy(() -> request.turns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsRequestsOutsideProviderSafetyBounds() {
        ChatTurn userTurn = new ChatTurn(ChatTurnRole.USER, "hello");

        assertThatThrownBy(() -> new ChatGenerationRequest(List.of(), "model", 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100 turns");
        assertThatThrownBy(() -> new ChatGenerationRequest(List.of(userTurn), " ", 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model must be non-blank");
        assertThatThrownBy(() -> new ChatGenerationRequest(List.of(userTurn), "model", 8_193, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputTokens");
        assertThatThrownBy(() -> new ChatGenerationRequest(List.of(userTurn), "model", 1, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    void rejectsOversizedAggregateContent() {
        String content = "x".repeat(30_000);
        List<ChatTurn> turns = List.of(
                user(content), user(content), user(content), user(content),
                user(content), user(content), user(content)
        );

        assertThatThrownBy(() -> new ChatGenerationRequest(turns, "model", 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 200000 characters");
    }

    @Test
    void enforcesDeltaAndTerminalChunkInvariants() {
        assertThat(ChatChunk.delta("answer")).isEqualTo(
                new ChatChunk("answer", false, null, null, null)
        );
        assertThat(ChatChunk.terminal(null, 12, 4)).isEqualTo(
                new ChatChunk("", true, "unknown", 12, 4)
        );
        assertThatThrownBy(() -> ChatChunk.delta(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delta must contain text");
        assertThatThrownBy(() -> new ChatChunk("answer", true, "stop", 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal chat chunk must not contain text");
        assertThatThrownBy(() -> ChatChunk.terminal("stop", -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputTokens must not be negative");
    }

    private ChatTurn user(String content) {
        return new ChatTurn(ChatTurnRole.USER, content);
    }
}
