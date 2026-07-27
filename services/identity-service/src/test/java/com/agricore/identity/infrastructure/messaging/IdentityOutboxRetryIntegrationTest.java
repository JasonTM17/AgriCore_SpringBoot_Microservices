package com.agricore.identity.infrastructure.messaging;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("unchecked")
class IdentityOutboxRetryIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void legacyWriteStateDisabledLeavesTheRowImmediatelyRetryable() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(new SerializationException("invalid payload")));
        OutboxPublisher publisher = publisher(kafka, false, 100, 100, 1);

        publisher.publishPending();
        publisher.publishPending();

        OutboxEventEntity failed = reload(event);
        assertThat(failed.getPublishAttempts()).isEqualTo(2);
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(failed.getQuarantinedAt()).isNull();
        verify(kafka, times(2)).send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void transientFailureNeverQuarantinesAtTheConfiguredMaximum() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(new org.apache.kafka.common.errors.TimeoutException("broker timeout")));

        publisher(kafka, true, 100, 100, 1).publishPending();

        OutboxEventEntity failed = reload(event);
        assertThat(failed.getPublishAttempts()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isNotNull();
        assertThat(failed.getQuarantinedAt()).isNull();
        assertThat(outboxRepository.countByPublishedAtIsNullAndQuarantinedAtIsNull()).isEqualTo(1);
    }

    @Test
    void wrappedPermanentFailureQuarantinesAndSanitizesItsDiagnostic() {
        OutboxEventEntity event = persistEvent();
        String unsafeDiagnostic = "bad\r\n\t" + "x".repeat(1_200);
        RuntimeException wrapped = new RuntimeException(
                "wrapper",
                new CompletionException(new SerializationException(unsafeDiagnostic))
        );
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(wrapped));
        OutboxPublisher publisher = publisher(kafka, true, 100, 100, 1);

        publisher.publishPending();
        publisher.publishPending();

        OutboxEventEntity quarantined = reload(event);
        assertThat(quarantined.getPublishAttempts()).isEqualTo(1);
        assertThat(quarantined.getNextAttemptAt()).isNull();
        assertThat(quarantined.getQuarantinedAt()).isNotNull();
        assertThat(quarantined.getLastError())
                .hasSize(1_000)
                .doesNotContain("\r", "\n", "\t");
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1);
        assertThat(outboxRepository.countByPublishedAtIsNullAndQuarantinedAtIsNull()).isZero();
        assertThat(outboxRepository.countByQuarantinedAtIsNotNull()).isEqualTo(1);
        verify(kafka).send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void deferredRowBecomesEligibleAndKeepsItsStableKafkaKey() throws Exception {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(
                        failedFuture(new RuntimeException("broker unavailable")),
                        CompletableFuture.completedFuture(mock(SendResult.class))
                );
        OutboxPublisher publisher = publisher(kafka, true, 100, 100, 10);

        publisher.publishPending();
        publisher.publishPending();
        verify(kafka).send(event.getTopic(), event.getId().toString(), event.getPayload());
        assertThat(reload(event).getPublishedAt()).isNull();

        Thread.sleep(150);
        publisher.publishPending();

        OutboxEventEntity published = reload(event);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(2);
        assertThat(published.getLastError()).isNull();
        verify(kafka, times(2)).send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void deferredPoisonCannotStarveTheNextBoundedPage() throws Exception {
        OutboxEventEntity poison = persistEvent();
        Thread.sleep(10);
        List<OutboxEventEntity> healthyEvents = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            healthyEvents.add(newEvent());
        }
        outboxRepository.saveAllAndFlush(healthyEvents);

        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation ->
                poison.getId().toString().equals(invocation.getArgument(1))
                        ? failedFuture(new SerializationException("poison"))
                        : CompletableFuture.completedFuture(mock(SendResult.class))
        );
        OutboxPublisher publisher = publisher(kafka, true, 60_000, 60_000, 10);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(reload(poison).getNextAttemptAt()).isNotNull();
        assertThat(healthyEvents.stream()
                .map(OutboxEventEntity::getId)
                .map(outboxRepository::findById)
                .map(java.util.Optional::orElseThrow)
                .filter(row -> row.getPublishedAt() != null))
                .hasSize(50);
        verify(kafka).send(poison.getTopic(), poison.getId().toString(), poison.getPayload());
    }

    private OutboxPublisher publisher(
            KafkaTemplate<String, String> kafka,
            boolean writeStateEnabled,
            long baseDelayMs,
            long maxDelayMs,
            int maxAttempts
    ) {
        return new OutboxPublisher(
                new OutboxPublicationStore(outboxRepository, transactionManager),
                kafka,
                5_000,
                30_000,
                new OutboxRetryProperties(baseDelayMs, maxDelayMs, maxAttempts, writeStateEnabled)
        );
    }

    private OutboxEventEntity persistEvent() {
        outboxRepository.deleteAll();
        return outboxRepository.saveAndFlush(newEvent());
    }

    private static OutboxEventEntity newEvent() {
        return OutboxEventEntity.create(
                "User",
                UUID.randomUUID().toString(),
                "UserRegistered.v1",
                "agricore.identity.events",
                "{\"eventType\":\"UserRegistered.v1\"}"
        );
    }

    private OutboxEventEntity reload(OutboxEventEntity event) {
        return outboxRepository.findById(event.getId()).orElseThrow();
    }

    private static CompletableFuture<SendResult<String, String>> failedFuture(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }
}
