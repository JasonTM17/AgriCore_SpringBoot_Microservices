package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.model.AssistantGeneration;

import java.util.Objects;

public record GenerationCancelResult(
        AssistantGeneration generation,
        boolean changed,
        boolean workerCancellationRequired
) {
    public GenerationCancelResult {
        Objects.requireNonNull(generation, "generation is required");
    }
}
