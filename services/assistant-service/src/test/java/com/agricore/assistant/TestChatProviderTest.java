package com.agricore.assistant;

import com.agricore.assistant.infrastructure.provider.ChatProvider;
import com.agricore.assistant.infrastructure.provider.TestChatProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestChatProviderTest {

    private final TestChatProvider provider = new TestChatProvider();

    @Test
    void refusesUnsafePrompts() {
        List<String> deltas = new ArrayList<>();
        String answer = provider.generate(List.of(), "drop table users", deltas::add);
        assertThat(answer).containsIgnoringCase("Refused");
        assertThat(deltas).isNotEmpty();
    }

    @Test
    void answersSafePromptsWithChunks() {
        List<String> deltas = new ArrayList<>();
        String answer = provider.generate(List.of(), "Tóm tắt tồn kho", deltas::add);
        assertThat(answer).contains("AgriCore test assistant");
        assertThat(deltas.size()).isGreaterThan(1);
        assertThat(String.join("", deltas)).isEqualTo(answer);
    }
}
