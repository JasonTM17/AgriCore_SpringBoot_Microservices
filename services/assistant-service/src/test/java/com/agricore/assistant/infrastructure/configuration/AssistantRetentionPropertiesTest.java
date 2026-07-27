package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantRetentionPropertiesTest {

    private final AssistantRetentionProperties properties =
            new AssistantRetentionProperties();

    @Test
    void rejectsUnsafeCleanupConfiguration() {
        assertThatThrownBy(() -> properties.setCleanupInterval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleanupInterval");
        assertThatThrownBy(() -> properties.setCleanupBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleanupBatchSize");
        assertThatThrownBy(() -> properties.setCleanupBatchSize(10_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleanupBatchSize");
    }
}
