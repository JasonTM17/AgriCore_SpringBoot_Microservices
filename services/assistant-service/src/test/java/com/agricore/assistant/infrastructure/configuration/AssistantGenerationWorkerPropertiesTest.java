package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantGenerationWorkerPropertiesTest {

    @Test
    void bindsBoundedWorkerControls() {
        AssistantGenerationWorkerProperties properties = bind(Map.of(
                "agricore.assistant.worker.enabled", "false",
                "agricore.assistant.worker.concurrency", "8",
                "agricore.assistant.worker.queue-capacity", "1024",
                "agricore.assistant.worker.lease-duration", "PT45S",
                "agricore.assistant.worker.heartbeat-interval", "PT15S",
                "agricore.assistant.worker.delta-batch-size", "32",
                "agricore.assistant.worker.delta-flush-interval", "PT0.2S"
        ));

        properties.validate();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getConcurrency()).isEqualTo(8);
        assertThat(properties.getQueueCapacity()).isEqualTo(1_024);
        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getDeltaBatchSize()).isEqualTo(32);
        assertThat(properties.getDeltaFlushInterval()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void defaultsAreEnabledBoundedAndHaveSafeLeaseTiming() {
        AssistantGenerationWorkerProperties properties = bind(Map.of());

        properties.validate();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getConcurrency()).isEqualTo(4);
        assertThat(properties.getQueueCapacity()).isEqualTo(512);
        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getDeltaBatchSize()).isEqualTo(16);
        assertThat(properties.getDeltaFlushInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void rejectsUnboundedOrUnsafeControls() {
        assertBindingFails("agricore.assistant.worker.concurrency", "33");
        assertBindingFails("agricore.assistant.worker.queue-capacity", "15");
        assertBindingFails("agricore.assistant.worker.lease-duration", "PT6M");
        assertBindingFails("agricore.assistant.worker.delta-batch-size", "65");
        assertBindingFails("agricore.assistant.worker.delta-flush-interval", "PT2S");

        AssistantGenerationWorkerProperties properties = bind(Map.of(
                "agricore.assistant.worker.lease-duration", "PT10S",
                "agricore.assistant.worker.heartbeat-interval", "PT10S"
        ));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shorter than leaseDuration");
    }

    private AssistantGenerationWorkerProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("agricore.assistant.worker", Bindable.of(AssistantGenerationWorkerProperties.class))
                .orElseGet(AssistantGenerationWorkerProperties::new);
    }

    private void assertBindingFails(String key, String value) {
        assertThatThrownBy(() -> bind(Map.of(key, value)))
                .isInstanceOf(BindException.class);
    }
}
