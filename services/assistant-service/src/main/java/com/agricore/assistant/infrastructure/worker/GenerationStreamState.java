package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.GenerationCompletion;

import java.time.Instant;
import java.util.List;

final class GenerationStreamState {

    private static final int MAX_RESPONSE_CHARACTERS = 200_000;

    private final StringBuilder response = new StringBuilder();
    private ChatChunk terminalChunk;
    private Instant firstTokenAt;

    String accept(List<ChatChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw GenerationProcessingException.failed("AI_PROVIDER_PROTOCOL_ERROR");
        }
        StringBuilder delta = new StringBuilder();
        for (ChatChunk chunk : chunks) {
            if (chunk == null) {
                throw GenerationProcessingException.failed("AI_PROVIDER_PROTOCOL_ERROR");
            }
            if (chunk.terminal()) {
                acceptTerminal(chunk);
            } else {
                acceptDelta(chunk.text(), delta);
            }
        }
        return delta.toString();
    }

    GenerationCompletion completion(Instant completedAt, Instant eventExpiresAt) {
        String completedContent = completedContent();
        return new GenerationCompletion(
                completedContent,
                terminalChunk.finishReason(),
                terminalChunk.inputTokens(),
                terminalChunk.outputTokens(),
                firstTokenAt,
                completedAt,
                eventExpiresAt
        );
    }

    String completedContent() {
        if (terminalChunk == null) {
            throw GenerationProcessingException.failed("AI_PROVIDER_INCOMPLETE_RESPONSE");
        }
        if (response.isEmpty() || response.toString().isBlank()) {
            throw GenerationProcessingException.failed("AI_PROVIDER_EMPTY_RESPONSE");
        }
        return response.toString();
    }

    String accumulatedContent() {
        return response.toString();
    }

    void observeFirstTokenAt(Instant observedAt) {
        if (firstTokenAt == null) {
            firstTokenAt = java.util.Objects.requireNonNull(
                    observedAt, "first token timestamp is required");
        }
    }

    private void acceptTerminal(ChatChunk chunk) {
        if (terminalChunk != null) {
            throw GenerationProcessingException.failed("AI_PROVIDER_PROTOCOL_ERROR");
        }
        terminalChunk = chunk;
    }

    private void acceptDelta(String value, StringBuilder batchDelta) {
        if (terminalChunk != null) {
            throw GenerationProcessingException.failed("AI_PROVIDER_PROTOCOL_ERROR");
        }
        if ((long) response.length() + value.length() > MAX_RESPONSE_CHARACTERS) {
            throw GenerationProcessingException.failed("AI_PROVIDER_RESPONSE_TOO_LARGE");
        }
        response.append(value);
        batchDelta.append(value);
    }
}
