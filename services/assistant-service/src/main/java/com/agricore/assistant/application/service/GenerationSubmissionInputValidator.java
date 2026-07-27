package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ToolEvidencePromptFormatter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class GenerationSubmissionInputValidator {

    private final ChatGenerationPolicy generationPolicy;
    private final ToolEvidencePromptFormatter promptFormatter;

    public GenerationSubmissionInputValidator(
            ChatGenerationPolicy generationPolicy,
            ToolEvidencePromptFormatter promptFormatter
    ) {
        this.generationPolicy = generationPolicy;
        this.promptFormatter = promptFormatter;
    }

    public void validatePrompt(String prompt, ToolEvidenceSnapshot evidence) {
        int reservedCharacters = promptFormatter.systemPolicy().length()
                + promptFormatter.renderEvidence(evidence).length();
        int maximumLength = Math.max(
                0, generationPolicy.maxInputCharacters() - reservedCharacters);
        if (prompt == null || prompt.isBlank() || prompt.strip().length() > maximumLength) {
            throw new IllegalArgumentException("Invalid generation prompt");
        }
    }

    public int estimateInputTokens(String prompt, ToolEvidenceSnapshot evidence) {
        int bytes = utf8Length(promptFormatter.systemPolicy())
                + utf8Length(promptFormatter.renderEvidence(evidence))
                + utf8Length(prompt == null ? "" : prompt.strip());
        return Math.max(1, (bytes + 2) / 3);
    }

    public String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.strip().length() > 128) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
        return idempotencyKey.strip();
    }

    public String requestHash(UUID conversationId, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((conversationId + "\n" + prompt)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
