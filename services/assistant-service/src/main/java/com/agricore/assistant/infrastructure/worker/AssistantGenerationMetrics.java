package com.agricore.assistant.infrastructure.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AssistantGenerationMetrics {

    private final Counter completed;
    private final Counter failed;
    private final Counter cancelled;

    public AssistantGenerationMetrics(MeterRegistry registry) {
        completed = counter(registry, "completed");
        failed = counter(registry, "failed");
        cancelled = counter(registry, "cancelled");
    }

    public void recordCompleted() {
        completed.increment();
    }

    public void recordFailed() {
        failed.increment();
    }

    public void recordCancelled() {
        cancelled.increment();
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("agricore.assistant.generations")
                .description("Durable assistant generation terminal outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
