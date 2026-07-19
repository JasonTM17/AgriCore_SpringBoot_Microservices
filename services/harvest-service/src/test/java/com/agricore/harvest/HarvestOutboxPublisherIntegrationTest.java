package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.infrastructure.messaging.OutboxPublisher;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class HarvestOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void publisher_commitsFailureThenPublishesTheSameRowWithoutSendingAgain() {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";
        OutboxEventEntity event = OutboxEventEntity.create(
                eventId,
                "HarvestBatch",
                UUID.randomUUID().toString(),
                "HarvestCompleted.v1",
                "agricore.harvest.events",
                payload
        );
        outboxRepository.saveAndFlush(event);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        CompletableFuture<SendResult<String, String>> succeeded =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(event.getTopic(), eventId.toString(), payload))
                .thenReturn(failed, succeeded);
        OutboxPublisher publisher = new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                5_000
        );

        publisher.publishPending();

        OutboxEventEntity failedAttempt = outboxRepository.findById(eventId).orElseThrow();
        assertThat(failedAttempt.getPublishedAt()).isNull();
        assertThat(failedAttempt.getPublishAttempts()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).contains("broker unavailable");

        publisher.publishPending();
        publisher.publishPending();

        OutboxEventEntity published = outboxRepository.findById(eventId).orElseThrow();
        assertThat(published.getId()).isEqualTo(eventId);
        assertThat(published.getPayload()).isEqualTo(payload);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(2);
        assertThat(published.getLastError()).isNull();
        assertThat(outboxRepository.count()).isEqualTo(1);
        verify(kafkaTemplate, times(2)).send(event.getTopic(), eventId.toString(), payload);
    }

    @Test
    void publisher_timesOutNeverCompletingSendAndRecordsFailure() {
        outboxRepository.deleteAll();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";
        OutboxEventEntity event = OutboxEventEntity.create(
                eventId,
                "HarvestBatch",
                UUID.randomUUID().toString(),
                "HarvestCompleted.v1",
                "agricore.harvest.events",
                payload
        );
        outboxRepository.saveAndFlush(event);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), eventId.toString(), payload))
                .thenReturn(new CompletableFuture<>());
        OutboxPublisher publisher = new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                50
        );

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
}
