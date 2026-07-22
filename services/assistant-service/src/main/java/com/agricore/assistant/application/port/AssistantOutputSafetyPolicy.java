package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.OutputSafetyAssessment;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;

public interface AssistantOutputSafetyPolicy {

    OutputSafetyAssessment evaluatePartial(String accumulatedContent, ToolEvidenceSnapshot evidence);

    OutputSafetyAssessment evaluateFinal(String completedContent, ToolEvidenceSnapshot evidence);
}
