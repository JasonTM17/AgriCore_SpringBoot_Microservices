package com.agricore.assistant.infrastructure.retention;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AssistantRetentionCleanupMetrics {

    private final Counter generationEvents;
    private final Counter conversations;
    private final Counter auditEvents;
    private final Counter failures;

    public AssistantRetentionCleanupMetrics(MeterRegistry registry) {
        generationEvents = purgedCounter(registry, "generation_events");
        conversations = purgedCounter(registry, "conversations");
        auditEvents = purgedCounter(registry, "audit_events");
        failures = Counter.builder("agricore.assistant.retention.cleanup.failures")
                .description("Assistant retention cleanup failures")
                .register(registry);
    }

    public void record(AssistantRetentionCleanupStore.CleanupResult result) {
        generationEvents.increment(result.generationEvents());
        conversations.increment(result.conversations());
        auditEvents.increment(result.auditEvents());
    }

    public void recordFailure() {
        failures.increment();
    }

    private static Counter purgedCounter(MeterRegistry registry, String dataset) {
        return Counter.builder("agricore.assistant.retention.purged")
                .description("Physically deleted assistant retention records")
                .tag("dataset", dataset)
                .register(registry);
    }
}
