package com.agricore.sales;

import com.agricore.sales.infrastructure.messaging.OutboxPublisher;
import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class SalesOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failedDeliveryIsCommittedThenTheSameRowPublishesOnce() {
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
                "SalesOrder",
                UUID.randomUUID().toString(),
                "SalesOrderCreated.v1",
                "agricore.sales.events",
                "{\"eventId\":\"" + eventId + "\"}"
        ));
    }

    private OutboxPublisher publisher(KafkaTemplate<String, String> kafkaTemplate, long timeoutMillis) {
        return new OutboxPublisher(outboxRepository, kafkaTemplate, transactionManager, timeoutMillis);
    }
}
