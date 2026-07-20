package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatChunk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationStreamStateTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

    @Test
    void preservesExactDeltasAndTerminalUsage() {
        GenerationStreamState state = new GenerationStreamState();

        assertThat(state.accept(List.of(
                ChatChunk.delta(" healthy"),
                ChatChunk.delta(" crop "),
                ChatChunk.terminal("STOP", 10, 3)
        ))).isEqualTo(" healthy crop ");
        var completion = state.completion(NOW, NOW.plusSeconds(30));

        assertThat(completion.content()).isEqualTo(" healthy crop ");
        assertThat(completion.finishReason()).isEqualTo("stop");
        assertThat(completion.inputTokens()).isEqualTo(10);
        assertThat(completion.outputTokens()).isEqualTo(3);
    }

    @Test
    void rejectsMissingTerminalChunksAfterTerminalAndOversizedOutput() {
        GenerationStreamState missingTerminal = new GenerationStreamState();
        missingTerminal.accept(List.of(ChatChunk.delta("partial")));
        assertFailureCode(
                () -> missingTerminal.completion(NOW, NOW.plusSeconds(30)),
                "AI_PROVIDER_INCOMPLETE_RESPONSE"
        );

        GenerationStreamState afterTerminal = new GenerationStreamState();
        assertFailureCode(() -> afterTerminal.accept(List.of(
                ChatChunk.terminal("stop", null, null),
                ChatChunk.delta("late")
        )), "AI_PROVIDER_PROTOCOL_ERROR");

        GenerationStreamState oversized = new GenerationStreamState();
        String maximumChunk = "x".repeat(65_536);
        oversized.accept(List.of(ChatChunk.delta(maximumChunk), ChatChunk.delta(maximumChunk)));
        oversized.accept(List.of(ChatChunk.delta(maximumChunk)));
        assertFailureCode(
                () -> oversized.accept(List.of(ChatChunk.delta("x".repeat(3_393)))),
                "AI_PROVIDER_RESPONSE_TOO_LARGE"
        );
    }

    private void assertFailureCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(GenerationProcessingException.class)
                .extracting("errorCode").isEqualTo(code);
    }
}
