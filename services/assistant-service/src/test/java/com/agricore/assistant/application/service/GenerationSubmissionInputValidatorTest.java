package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ToolEvidencePromptFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationSubmissionInputValidatorTest {

    @Test
    void estimatesUnicodeInputConservativelyFromUtf8Bytes() {
        ChatGenerationPolicy policy = mock(ChatGenerationPolicy.class);
        ToolEvidencePromptFormatter formatter = mock(ToolEvidencePromptFormatter.class);
        when(formatter.systemPolicy()).thenReturn("policy");
        when(formatter.renderEvidence(any())).thenReturn("evidence");

        GenerationSubmissionInputValidator validator =
                new GenerationSubmissionInputValidator(policy, formatter);

        assertThat(validator.estimateInputTokens("Cây lúa 🌱", ToolEvidenceSnapshot.empty()))
                .isEqualTo(10);
    }
}
