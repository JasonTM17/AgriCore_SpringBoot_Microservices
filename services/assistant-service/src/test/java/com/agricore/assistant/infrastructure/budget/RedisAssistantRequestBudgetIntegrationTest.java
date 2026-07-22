package com.agricore.assistant.infrastructure.budget;

import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.infrastructure.configuration.AssistantBudgetProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisAssistantRequestBudgetIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private AssistantBudgetProperties properties;
    private RedisAssistantRequestBudget budget;
    private AssistantActor actor;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        properties = new AssistantBudgetProperties();
        properties.setMaxTokens(100_000);
        budget = new RedisAssistantRequestBudget(redis, properties);
        actor = new AssistantActor(UUID.randomUUID(), List.of("FIELD_WORKER"));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void reservesRequestQuotaAtomicallyUnderConcurrency() throws Exception {
        properties.setMaxRequests(5);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 20; index++) {
                String reservationId = "request-" + index;
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        budget.reserve(actor, "198.51.100.4", reservationId, 10);
                        return true;
                    } catch (AssistantException exception) {
                        assertThat(exception.getCode())
                                .isEqualTo("ASSISTANT_REQUEST_BUDGET_EXCEEDED");
                        return false;
                    }
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void enforcesTokenQuotaAcrossRequests() {
        properties.setMaxRequests(100);
        properties.setMaxTokens(100);

        budget.reserve(actor, "198.51.100.4", "token-request", 60);

        assertThatThrownBy(() -> budget.reserve(actor, "198.51.100.4", "token-request", 110))
                .isInstanceOf(AssistantException.class)
                .hasFieldOrPropertyWithValue("code", "ASSISTANT_REQUEST_BUDGET_EXCEEDED");
    }

    @Test
    void tokenSupplementDoesNotConsumeAnotherRequestSlot() {
        properties.setMaxRequests(1);
        properties.setMaxTokens(100);

        budget.reserve(actor, "198.51.100.4", "same-request", 10);
        budget.reserve(actor, "198.51.100.4", "same-request", 20);

        assertThatThrownBy(() -> budget.reserve(actor, "198.51.100.4", "different-request", 10))
                .isInstanceOf(AssistantException.class)
                .hasFieldOrPropertyWithValue("code", "ASSISTANT_REQUEST_BUDGET_EXCEEDED");
    }

    @Test
    void sameIdempotentReservationConsumesOneRequestSlotUnderConcurrency() throws Exception {
        properties.setMaxRequests(1);
        properties.setMaxTokens(100);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        budget.reserve(actor, "198.51.100.4", "same-concurrent-request", 20);
                        return true;
                    } catch (AssistantException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(20);
            assertThatThrownBy(() -> budget.reserve(
                    actor, "198.51.100.4", "another-request", 20))
                    .isInstanceOf(AssistantException.class)
                    .hasFieldOrPropertyWithValue("code", "ASSISTANT_REQUEST_BUDGET_EXCEEDED");
        } finally {
            executor.shutdownNow();
        }
    }
}
