package com.agricore.work;

import com.agricore.work.infrastructure.messaging.OutboxRetryProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryPropertiesTest {

    @Test
    void rejectsUnsafeRetryConfiguration() {
        assertThatThrownBy(() -> new OutboxRetryProperties(1_000, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRetryProperties(99, 100, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRetryProperties(100, 86_400_001, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRetryProperties(100, 100, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capsExponentialDelay() {
        OutboxRetryProperties properties = new OutboxRetryProperties(100, 500, 10);

        assertThat(properties.delayForFailure(1)).isEqualTo(100);
        assertThat(properties.delayForFailure(2)).isEqualTo(200);
        assertThat(properties.delayForFailure(3)).isEqualTo(400);
        assertThat(properties.delayForFailure(4)).isEqualTo(500);
        assertThat(properties.delayForFailure(10)).isEqualTo(500);
    }
}
