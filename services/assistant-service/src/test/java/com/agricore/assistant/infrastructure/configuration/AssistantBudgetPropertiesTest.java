package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AssistantBudgetPropertiesTest {

    @Test
    void rejectsSubSecondWindowsThatWouldProduceZeroRedisTtl() {
        AssistantBudgetProperties properties = new AssistantBudgetProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setWindow(Duration.ofMillis(999)))
                .withMessageContaining("one second");
    }
}
