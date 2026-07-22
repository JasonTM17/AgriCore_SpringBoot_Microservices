package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;

public interface ToolEvidencePromptFormatter {
    String systemPolicy();

    String renderEvidence(ToolEvidenceSnapshot evidence);
}
