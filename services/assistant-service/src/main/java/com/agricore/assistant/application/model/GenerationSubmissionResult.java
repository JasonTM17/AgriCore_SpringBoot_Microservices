package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantMessage;

public record GenerationSubmissionResult(
        AssistantGeneration generation,
        AssistantMessage userMessage,
        boolean deduplicated
) {
}
