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

class AssistantGenerationEventStreamPropertiesTest {

    @Test
    void defaultsBoundConnectionsThreadsPollingAndLifetime() {
        AssistantGenerationEventStreamProperties properties = bind(Map.of());

        properties.validate();
        assertThat(properties.getMaxConnections()).isEqualTo(64);
        assertThat(properties.getSchedulerThreads()).isEqualTo(2);
        assertThat(properties.getBatchSize()).isEqualTo(100);
        assertThat(properties.getPollInterval()).isEqualTo(Duration.ofMillis(750));
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getMaxConnectionDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void bindsExplicitStreamResourceControls() {
        AssistantGenerationEventStreamProperties properties = bind(Map.of(
                "agricore.assistant.streaming.max-connections", "20",
                "agricore.assistant.streaming.scheduler-threads", "4",
                "agricore.assistant.streaming.batch-size", "250",
                "agricore.assistant.streaming.poll-interval", "PT1S",
                "agricore.assistant.streaming.heartbeat-interval", "PT20S",
                "agricore.assistant.streaming.max-connection-duration", "PT10M"
        ));

        properties.validate();
        assertThat(properties.getMaxConnections()).isEqualTo(20);
        assertThat(properties.getSchedulerThreads()).isEqualTo(4);
        assertThat(properties.getBatchSize()).isEqualTo(250);
        assertThat(properties.getPollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getMaxConnectionDuration()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void rejectsUnboundedOrUnsafeStreamControls() {
        assertBindingFails("agricore.assistant.streaming.max-connections", "1001");
        assertBindingFails("agricore.assistant.streaming.scheduler-threads", "17");
        assertBindingFails("agricore.assistant.streaming.batch-size", "1001");
        assertBindingFails("agricore.assistant.streaming.poll-interval", "PT0.05S");
        assertBindingFails("agricore.assistant.streaming.heartbeat-interval", "PT2M");
        assertBindingFails("agricore.assistant.streaming.max-connection-duration", "PT31M");

        AssistantGenerationEventStreamProperties properties = bind(Map.of(
                "agricore.assistant.streaming.poll-interval", "PT2S",
                "agricore.assistant.streaming.heartbeat-interval", "PT1S"
        ));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heartbeatInterval");
    }

    private AssistantGenerationEventStreamProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind(
                        "agricore.assistant.streaming",
                        Bindable.of(AssistantGenerationEventStreamProperties.class)
                )
                .orElseGet(AssistantGenerationEventStreamProperties::new);
    }

    private void assertBindingFails(String key, String value) {
        assertThatThrownBy(() -> bind(Map.of(key, value)))
                .isInstanceOf(BindException.class);
    }
}
