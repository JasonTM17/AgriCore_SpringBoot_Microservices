package com.agricore.assistant.infrastructure.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantGenerationMetricsTest {

    @Test
    void recordsDurableTerminalOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AssistantGenerationMetrics metrics = new AssistantGenerationMetrics(registry);

        metrics.recordCompleted();
        metrics.recordFailed();
        metrics.recordCancelled();

        assertThat(registry.get("agricore.assistant.generations").tag("outcome", "completed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.assistant.generations").tag("outcome", "failed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agricore.assistant.generations").tag("outcome", "cancelled").counter().count())
                .isEqualTo(1);
    }
}
