package com.agricore.identity.infrastructure.messaging;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("unchecked")
class IdentityOutboxPublisherIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void successfulSendRunsOutsideDatabaseTransactionAndCompletesClaim() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return CompletableFuture.completedFuture(mock(SendResult.class));
                });

        publisher(kafkaTemplate, 5_000, 30_000).publishPending();

        OutboxEventEntity published = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(1);
        assertThat(published.getLastError()).isNull();
        assertThat(published.getClaimToken()).isNull();
        assertThat(published.getClaimUntil()).isNull();
        verify(kafkaTemplate).send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void timedOutSendReleasesClaimAndTheSameRowCanRetry() {
        OutboxEventEntity event = persistEvent();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> neverCompletes = new CompletableFuture<>();
        CompletableFuture<SendResult<String, String>> succeeded =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenReturn(neverCompletes, succeeded);
        OutboxPublisher publisher = publisher(kafkaTemplate, 50, 1_000);

        long startedAt = System.nanoTime();
        publisher.publishPending();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        OutboxEventEntity failed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getPublishAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("timed out");
        assertThat(failed.getClaimToken()).isNull();
        assertThat(failed.getClaimUntil()).isNull();

        publisher.publishPending();

        OutboxEventEntity published = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishAttempts()).isEqualTo(2);
        assertThat(published.getLastError()).isNull();
        verify(kafkaTemplate, times(2))
                .send(event.getTopic(), event.getId().toString(), event.getPayload());
    }

    @Test
    void constructorRejectsUnboundedOrUnsafeDurations() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        assertThatThrownBy(() -> publisher(kafkaTemplate, 0, 30_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send timeout");
        assertThatThrownBy(() -> publisher(kafkaTemplate, 5_000, 5_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim lease");
        assertThatThrownBy(() -> publisher(kafkaTemplate, 60_001, 70_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("send timeout");
    }

    private OutboxPublisher publisher(
            KafkaTemplate<String, String> kafkaTemplate,
            long timeoutMillis,
        long claimLeaseMillis
    ) {
        return new OutboxPublisher(
                new OutboxPublicationStore(outboxRepository, transactionManager),
                kafkaTemplate,
                timeoutMillis,
                claimLeaseMillis
        );
    }

    private OutboxEventEntity persistEvent() {
        outboxRepository.deleteAll();
        OutboxEventEntity event = OutboxEventEntity.create(
                "User",
                UUID.randomUUID().toString(),
                "UserRegistered.v1",
                "agricore.identity.events",
                "{\"eventType\":\"UserRegistered.v1\"}"
        );
        return outboxRepository.saveAndFlush(event);
    }
}
