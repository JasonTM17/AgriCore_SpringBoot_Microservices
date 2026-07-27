package com.agricore.traceability;

import com.agricore.traceability.infrastructure.messaging.OutboxPublisher;
import com.agricore.traceability.infrastructure.messaging.OutboxRetryProperties;
import com.agricore.traceability.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class TraceabilityOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failedDeliveryIsCommittedThenTheSameRowPublishesOnce() throws InterruptedException {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failed, CompletableFuture.completedFuture(mock(SendResult.class)));
        OutboxPublisher publisher = publisher(kafkaTemplate, 5_000);

        publisher.publishPending();
        OutboxEventEntity failedAttempt = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(failedAttempt.getPublishedAt()).isNull();
        assertThat(failedAttempt.getPublishAttempts()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).contains("broker unavailable");

        Thread.sleep(150);
        publisher.publishPending();
        publisher.publishPending();
        OutboxEventEntity published = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(2);
        assertThat(published.getLastError()).isNull();
        verify(kafkaTemplate, times(2))
                .send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void neverCompletingSendTimesOutAndRecordsFailure() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(new CompletableFuture<>());

        long startedAt = System.nanoTime();
        publisher(kafkaTemplate, 50).publishPending();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        OutboxEventEntity failed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getPublishAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("timed out");
    }

    @Test
    void poisonEventIsQuarantinedWithoutStarvingHealthyEvent() throws InterruptedException {
        outboxRepository.deleteAll();
        UUID poisonId = UUID.randomUUID();
        OutboxEventEntity poison = OutboxEventEntity.create(
                poisonId, "TraceabilityBatch", UUID.randomUUID().toString(), "Poison.v1",
                "agricore.traceability.events", "{\"eventId\":\"" + poisonId + "\"}"
        );
        outboxRepository.saveAndFlush(poison);
        Thread.sleep(2);
        UUID healthyId = UUID.randomUUID();
        OutboxEventEntity healthy = OutboxEventEntity.create(
                healthyId, "TraceabilityBatch", UUID.randomUUID().toString(), "Healthy.v1",
                "agricore.traceability.events", "{\"eventId\":\"" + healthyId + "\"}"
        );
        outboxRepository.saveAndFlush(healthy);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.SerializationException("poison"));
        when(kafkaTemplate.send(poison.getTopic(), poisonId.toString(), poison.getPayload()))
                .thenReturn(failed);
        when(kafkaTemplate.send(healthy.getTopic(), healthyId.toString(), healthy.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher(kafkaTemplate, 5_000, new OutboxRetryProperties(100, 100, 1)).publishPending();

        OutboxEventEntity quarantined = outboxRepository.findById(poisonId).orElseThrow();
        assertThat(quarantined.getPublishedAt()).isNull();
        assertThat(quarantined.getQuarantinedAt()).isNotNull();
        assertThat(quarantined.getNextAttemptAt()).isNull();
        assertThat(outboxRepository.findById(healthyId).orElseThrow().getPublishedAt()).isNotNull();
        verify(kafkaTemplate).send(poison.getTopic(), poisonId.toString(), poison.getPayload());
        verify(kafkaTemplate).send(healthy.getTopic(), healthyId.toString(), healthy.getPayload());
    }

    @Test
    void deferredPoisonBatchDoesNotStarveTheNextHealthyPage() throws InterruptedException {
        outboxRepository.deleteAll();
        List<OutboxEventEntity> poisonEvents = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID eventId = UUID.randomUUID();
            poisonEvents.add(OutboxEventEntity.create(
                    eventId, "TraceabilityBatch", UUID.randomUUID().toString(), "Poison.v1",
                    "agricore.traceability.events", "{\"eventId\":\"" + eventId + "\"}"
            ));
        }
        outboxRepository.saveAllAndFlush(poisonEvents);
        Thread.sleep(5);
        UUID healthyId = UUID.randomUUID();
        OutboxEventEntity healthy = OutboxEventEntity.create(
                healthyId, "TraceabilityBatch", UUID.randomUUID().toString(), "Healthy.v1",
                "agricore.traceability.events", "{\"eventId\":\"" + healthyId + "\"}"
        );
        outboxRepository.saveAndFlush(healthy);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.SerializationException("poison"));
        when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation ->
                healthyId.toString().equals(invocation.getArgument(1))
                        ? CompletableFuture.completedFuture(mock(SendResult.class))
                        : failed);
        OutboxPublisher publisher = publisher(
                kafkaTemplate,
                5_000,
                new OutboxRetryProperties(60_000, 60_000, 10)
        );

        publisher.publishPending();
        publisher.publishPending();

        assertThat(outboxRepository.findById(healthyId).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(poisonEvents.stream()
                .map(OutboxEventEntity::getId)
                .map(outboxRepository::findById)
                .map(java.util.Optional::orElseThrow)
                .allMatch(row -> row.getNextAttemptAt() != null && row.getQuarantinedAt() == null))
                .isTrue();
    }

    @Test
    void concurrentPublisherSkipsAnInFlightLockedRow() throws Exception {
        OutboxEventEntity event = persistEvent();
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> firstResult = new CompletableFuture<>();
        KafkaTemplate<String, String> firstKafka = mock(KafkaTemplate.class);
        when(firstKafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenAnswer(ignored -> {
                    firstSendStarted.countDown();
                    return firstResult;
                });
        KafkaTemplate<String, String> secondKafka = mock(KafkaTemplate.class);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRun = executor.submit(publisher(firstKafka, 5_000)::publishPending);
            assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var secondRun = executor.submit(publisher(secondKafka, 5_000)::publishPending);
            secondRun.get(1, TimeUnit.SECONDS);

            verify(secondKafka, never())
                    .send(event.getTopic(), event.getId().toString(), event.getPayload());
            firstResult.complete(mock(SendResult.class));
            firstRun.get(5, TimeUnit.SECONDS);
        } finally {
            firstResult.complete(mock(SendResult.class));
        }
    }

    private OutboxEventEntity persistEvent() {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        return outboxRepository.saveAndFlush(OutboxEventEntity.create(
                eventId,
                "TraceabilityBatch",
                UUID.randomUUID().toString(),
                "TraceabilityBatchCreated.v1",
                "agricore.traceability.events",
                "{\"eventId\":\"" + eventId + "\"}"
        ));
    }

    private OutboxPublisher publisher(KafkaTemplate<String, String> kafkaTemplate, long timeoutMillis) {
        return publisher(kafkaTemplate, timeoutMillis, new OutboxRetryProperties(100, 100, 10));
    }

    private OutboxPublisher publisher(
            KafkaTemplate<String, String> kafkaTemplate,
            long timeoutMillis,
            OutboxRetryProperties retryProperties
    ) {
        return new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                timeoutMillis,
                retryProperties
        );
    }
}
