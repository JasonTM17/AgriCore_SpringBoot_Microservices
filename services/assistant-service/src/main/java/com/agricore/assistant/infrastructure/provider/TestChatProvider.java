package com.agricore.assistant.infrastructure.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Deterministic provider for tests and local demos without external keys.
 */
@Component
public class TestChatProvider implements ChatProvider {

    private static final List<String> UNSAFE = List.of(
            "drop table",
            "delete from",
            "rm -rf",
            "exfiltrate",
            "bypass auth",
            "ignore previous instructions"
    );

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String generate(List<ChatMessage> history, String userPrompt, Consumer<String> onDelta) {
        String lower = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        for (String banned : UNSAFE) {
            if (lower.contains(banned)) {
                String refusal = "Refused: request is out of scope for read-only AgriCore assistant tools.";
                onDelta.accept(refusal);
                return refusal;
            }
        }
        String answer = "AgriCore test assistant received: " + (userPrompt == null ? "" : userPrompt.trim())
                + ". Read-only tools only; no writes.";
        // Emit in small chunks to exercise SSE delta path.
        int i = 0;
        while (i < answer.length()) {
            int end = Math.min(i + 24, answer.length());
            onDelta.accept(answer.substring(i, end));
            i = end;
        }
        return answer;
    }
}
