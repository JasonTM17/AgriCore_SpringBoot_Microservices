package com.agricore.assistant.api.streaming;

import com.agricore.assistant.domain.exception.AssistantException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationEventCursorResolverTest {

    private final GenerationEventCursorResolver resolver = new GenerationEventCursorResolver();

    @Test
    void usesHighestValidQueryOrReconnectCursor() {
        assertThat(resolver.resolve(-1, null)).isEqualTo(-1);
        assertThat(resolver.resolve(4, " 7 ")).isEqualTo(7);
        assertThat(resolver.resolve(9, "7")).isEqualTo(9);
    }

    @Test
    void rejectsNegativeMalformedAndOverflowingHeaderValues() {
        assertInvalid(() -> resolver.resolve(-2, null));
        assertInvalid(() -> resolver.resolve(-1, "-1"));
        assertInvalid(() -> resolver.resolve(-1, "not-a-sequence"));
        assertInvalid(() -> resolver.resolve(-1, "999999999999999999999999"));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AssistantException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_EVENT_CURSOR");
                    assertThat(exception.getHttpStatus()).isEqualTo(400);
                });
    }
}
