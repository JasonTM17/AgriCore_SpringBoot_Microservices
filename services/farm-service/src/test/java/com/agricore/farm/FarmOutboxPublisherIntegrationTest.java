package com.agricore.farm;

import com.agricore.farm.infrastructure.messaging.OutboxPublisher;
import com.agricore.farm.infrastructure.messaging.OutboxRetryProperties;
import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class FarmOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failureIsCommittedThenTheSameRowPublishesOnce() throws InterruptedException {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";
        OutboxEventEntity event = event(eventId, payload);
        outboxRepository.saveAndFlush(event);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        CompletableFuture<SendResult<String, String>> succeeded =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(event.getTopic(), eventId.toString(), payload))
                .thenReturn(failed, succeeded);
        OutboxPublisher publisher = publisher(kafkaTemplate, 5_000);

        publisher.publishPending();

        OutboxEventEntity failedAttempt = outboxRepository.findById(eventId).orElseThrow();
        assertThat(failedAttempt.getPublishedAt()).isNull();
        assertThat(failedAttempt.getPublishAttempts()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).contains("broker unavailable");

        Thread.sleep(150);
        publisher.publishPending();
        publisher.publishPending();

        OutboxEventEntity published = outboxRepository.findById(eventId).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(2);
        assertThat(published.getLastError()).isNull();
        assertThat(outboxRepository.count()).isEqualTo(1);
        verify(kafkaTemplate, times(2)).send(event.getTopic(), eventId.toString(), payload);
    }

    @Test
    void neverCompletingSendTimesOutAndRecordsFailure() {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";
        OutboxEventEntity event = event(eventId, payload);
        outboxRepository.saveAndFlush(event);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), eventId.toString(), payload))
                .thenReturn(new CompletableFuture<>());
        OutboxPublisher publisher = publisher(kafkaTemplate, 50);

        long startedAt = System.nanoTime();
        publisher.publishPending();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        OutboxEventEntity failed = outboxRepository.findById(eventId).orElseThrow();
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getPublishAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("timed out");
        verify(kafkaTemplate).send(event.getTopic(), eventId.toString(), payload);
    }

    @Test
    void deferredPoisonDoesNotConsumeTheNextBoundedBatch() throws InterruptedException {
        outboxRepository.deleteAll();
        UUID poisonId = UUID.randomUUID();
        OutboxEventEntity poison = event(poisonId, "{\"eventId\":\"" + poisonId + "\"}");
        outboxRepository.saveAndFlush(poison);
        Thread.sleep(5);

        List<OutboxEventEntity> healthyEvents = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID eventId = UUID.randomUUID();
            healthyEvents.add(event(eventId, "{\"eventId\":\"" + eventId + "\"}"));
        }
        outboxRepository.saveAllAndFlush(healthyEvents);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.SerializationException("poison"));
        when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation ->
                poisonId.toString().equals(invocation.getArgument(1))
                        ? failed
                        : CompletableFuture.completedFuture(mock(SendResult.class)));
        OutboxPublisher publisher = new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                5_000,
                new OutboxRetryProperties(60_000, 60_000, 10)
        );

        publisher.publishPending();
        publisher.publishPending();

        OutboxEventEntity deferred = outboxRepository.findById(poisonId).orElseThrow();
        assertThat(deferred.getNextAttemptAt()).isNotNull();
        assertThat(deferred.getQuarantinedAt()).isNull();
        assertThat(healthyEvents.stream()
                .map(OutboxEventEntity::getId)
                .map(outboxRepository::findById)
                .map(java.util.Optional::orElseThrow)
                .filter(row -> row.getPublishedAt() != null))
                .hasSize(50);
        verify(kafkaTemplate, times(1)).send(poison.getTopic(), poisonId.toString(), poison.getPayload());
    }

    @Test
    void terminalFailureQuarantinesTheRow() {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        OutboxEventEntity event = event(eventId, "{\"eventId\":\"" + eventId + "\"}");
        event.markFailed("previous failure", java.time.Instant.now().minusSeconds(1), 100, 2);
        outboxRepository.saveAndFlush(event);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new org.apache.kafka.common.errors.SerializationException("still broken"));
        when(kafkaTemplate.send(event.getTopic(), eventId.toString(), event.getPayload())).thenReturn(failed);

        new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                5_000,
                new OutboxRetryProperties(100, 100, 2)
        ).publishPending();

        OutboxEventEntity quarantined = outboxRepository.findById(eventId).orElseThrow();
        assertThat(quarantined.getPublishAttempts()).isEqualTo(2);
        assertThat(quarantined.getQuarantinedAt()).isNotNull();
        assertThat(quarantined.getNextAttemptAt()).isNull();
        assertThat(outboxRepository.countByQuarantinedAtIsNotNull()).isEqualTo(1);
    }

    private OutboxPublisher publisher(KafkaTemplate<String, String> kafkaTemplate, long timeoutMillis) {
        return new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                timeoutMillis,
                new OutboxRetryProperties(100, 100, 10)
        );
    }

    private static OutboxEventEntity event(UUID eventId, String payload) {
        return OutboxEventEntity.create(
                eventId,
                "Farm",
                UUID.randomUUID().toString(),
                "FarmCreated.v1",
                "agricore.farm.events",
                payload
        );
    }
}
