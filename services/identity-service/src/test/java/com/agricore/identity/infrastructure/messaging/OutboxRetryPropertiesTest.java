package com.agricore.identity.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RetryPropertiesConfiguration.class);

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

    @Test
    void bindsRuntimeConfigurationIncludingDisabledWriteState() {
        contextRunner.withPropertyValues(
                "agricore.outbox.publisher.retry.base-delay-ms=250",
                "agricore.outbox.publisher.retry.max-delay-ms=2000",
                "agricore.outbox.publisher.retry.max-attempts=7",
                "agricore.outbox.publisher.retry.write-state-enabled=false"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            OutboxRetryProperties properties = context.getBean(OutboxRetryProperties.class);
            assertThat(properties.baseDelayMs()).isEqualTo(250);
            assertThat(properties.maxDelayMs()).isEqualTo(2_000);
            assertThat(properties.maxAttempts()).isEqualTo(7);
            assertThat(properties.writeStateEnabled()).isFalse();
        });
    }

    @Test
    void invalidRuntimeConfigurationFailsBinding() {
        contextRunner.withPropertyValues(
                "agricore.outbox.publisher.retry.base-delay-ms=99",
                "agricore.outbox.publisher.retry.max-delay-ms=2000",
                "agricore.outbox.publisher.retry.max-attempts=7"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxRetryProperties.class)
    static class RetryPropertiesConfiguration {
    }
}
