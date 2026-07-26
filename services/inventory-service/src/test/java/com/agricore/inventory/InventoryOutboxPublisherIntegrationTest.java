package com.agricore.inventory;

import com.agricore.inventory.infrastructure.messaging.OutboxPublisher;
import com.agricore.inventory.infrastructure.messaging.OutboxRetryProperties;
import com.agricore.inventory.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.OutboxEventEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class InventoryOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failureIsDeferredThenTheSameRowPublishesOnce() throws InterruptedException {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(
                        failedFuture(new IllegalStateException("broker unavailable")),
                        CompletableFuture.completedFuture(mock(SendResult.class))
                );
        OutboxPublisher publisher = publisher(kafkaTemplate, 5_000);

        publisher.publishPending();
        OutboxEventEntity failedAttempt = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(failedAttempt.getPublishAttempts()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).contains("broker unavailable");
        assertThat(failedAttempt.getNextAttemptAt()).isNotNull();

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
    void neverCompletingSendUsesDatabaseAnchoredFailureTime() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(new CompletableFuture<>());

        long startedAt = System.nanoTime();
        publisher(kafkaTemplate, 50).publishPending();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        OutboxEventEntity failed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(failed.getLastError()).contains("timed out");
        assertThat(failed.getNextAttemptAt()).isAfter(outboxRepository.currentTimestamp());
    }

    @Test
    void rolloutGuardRecordsLegacyFailureWithoutWritingRetryState() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(new IllegalStateException("broker unavailable")));

        publisher(kafkaTemplate, 5_000, new OutboxRetryProperties(100, 100, 1, false))
                .publishPending();

        OutboxEventEntity failed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getPublishAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("broker unavailable");
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(failed.getQuarantinedAt()).isNull();
    }

    @Test
    void transientFailureAtMaxAttemptsOneRemainsDeferred() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(new IllegalStateException("broker unavailable")));

        publisher(kafkaTemplate, 5_000, new OutboxRetryProperties(100, 100, 1))
                .publishPending();

        OutboxEventEntity deferred = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(deferred.getPublishAttempts()).isEqualTo(1);
        assertThat(deferred.getNextAttemptAt()).isNotNull();
        assertThat(deferred.getQuarantinedAt()).isNull();
    }

    @Test
    void wrappedPermanentFailureIsQuarantinedWithBoundedSanitizedDiagnostic() {
        OutboxEventEntity event = persistEvent();
        String diagnostic = "poison\r\n\t" + "x".repeat(1_200);
        SerializationException permanent = new SerializationException(diagnostic);
        KafkaProducerException wrapped = new KafkaProducerException(
                new ProducerRecord<>(event.getTopic(), event.getId().toString(), event.getPayload()),
                "send failed",
                permanent
        );
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(failedFuture(wrapped));

        publisher(kafkaTemplate, 5_000, new OutboxRetryProperties(100, 100, 1))
                .publishPending();

        OutboxEventEntity quarantined = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(quarantined.getQuarantinedAt()).isNotNull();
        assertThat(quarantined.getNextAttemptAt()).isNull();
        assertThat(quarantined.getLastError())
                .hasSize(1_000)
                .doesNotContain("\r", "\n", "\t")
                .startsWith("poison");
    }

    @Test
    void deferredPoisonPageDoesNotStarveHealthyFiftyFirstEvent() throws InterruptedException {
        outboxRepository.deleteAll();
        List<OutboxEventEntity> poisonEvents = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            poisonEvents.add(event(UUID.randomUUID(), "Poison.v1"));
        }
        outboxRepository.saveAllAndFlush(poisonEvents);
        Thread.sleep(5);
        OutboxEventEntity healthy = outboxRepository.saveAndFlush(event(UUID.randomUUID(), "Healthy.v1"));

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed =
                failedFuture(new SerializationException("poison"));
        when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation ->
                healthy.getId().toString().equals(invocation.getArgument(1))
                        ? CompletableFuture.completedFuture(mock(SendResult.class))
                        : failed);
        OutboxPublisher publisher = publisher(
                kafkaTemplate,
                5_000,
                new OutboxRetryProperties(60_000, 60_000, 10)
        );

        publisher.publishPending();
        publisher.publishPending();

        assertThat(outboxRepository.findById(healthy.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(poisonEvents)
                .allSatisfy(poison -> {
                    OutboxEventEntity deferred = outboxRepository.findById(poison.getId()).orElseThrow();
                    assertThat(deferred.getNextAttemptAt()).isNotNull();
                    assertThat(deferred.getQuarantinedAt()).isNull();
                });
    }

    private OutboxEventEntity persistEvent() {
        outboxRepository.deleteAll();
        return outboxRepository.saveAndFlush(event(UUID.randomUUID(), "StockAdded.v1"));
    }

    private static OutboxEventEntity event(UUID eventId, String eventType) {
        return OutboxEventEntity.create(
                eventId,
                "InventoryItem",
                UUID.randomUUID().toString(),
                eventType,
                "agricore.inventory.events",
                "{\"eventId\":\"" + eventId + "\"}"
        );
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

    private static CompletableFuture<SendResult<String, String>> failedFuture(Throwable failure) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }
}
