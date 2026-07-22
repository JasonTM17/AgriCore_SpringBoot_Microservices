package com.agricore.assistant.infrastructure.budget;

import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.infrastructure.configuration.AssistantBudgetProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RedisAssistantRequestBudgetTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AssistantBudgetProperties properties = new AssistantBudgetProperties();
    private final RedisAssistantRequestBudget budget = new RedisAssistantRequestBudget(redis, properties);
    private final AssistantActor actor = new AssistantActor(UUID.randomUUID(), List.of("FIELD_WORKER"));

    @Test
    void permitsAtomicReservationWhenScriptReturnsAllowed() {
        doReturn(0L).when(redis).execute(
                anyRedisScript(), anyList(), any(Object[].class));

        budget.reserve(actor, "127.0.0.1", "request-1", 100);
    }

    @Test
    void mapsScriptLimitResultToRateLimitError() {
        doReturn(3L).when(redis).execute(
                anyRedisScript(), anyList(), any(Object[].class));

        assertThatThrownBy(() -> budget.reserve(actor, "127.0.0.1", "request-1", 100))
                .isInstanceOf(AssistantException.class)
                .hasFieldOrPropertyWithValue("code", "ASSISTANT_REQUEST_BUDGET_EXCEEDED");
    }

    @Test
    void deniesWhenRedisIsUnavailable() {
        doThrow(new IllegalStateException("redis unavailable")).when(redis).execute(
                anyRedisScript(), anyList(), any(Object[].class));

        assertThatThrownBy(() -> budget.reserve(actor, "127.0.0.1", "request-1", 100))
                .isInstanceOf(AssistantException.class)
                .hasFieldOrPropertyWithValue("code", "ASSISTANT_BUDGET_UNAVAILABLE");
    }

    private static RedisScript<Long> anyRedisScript() {
        return any();
    }
}
