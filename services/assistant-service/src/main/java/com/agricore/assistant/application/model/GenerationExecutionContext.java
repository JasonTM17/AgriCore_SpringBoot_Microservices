package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.model.AssistantGeneration;
import com.agricore.assistant.domain.model.AssistantMessage;

import java.util.List;
import java.util.Objects;

public record GenerationExecutionContext(
        AssistantGeneration generation,
        List<AssistantMessage> messages
) {
    public GenerationExecutionContext {
        Objects.requireNonNull(generation, "generation is required");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
