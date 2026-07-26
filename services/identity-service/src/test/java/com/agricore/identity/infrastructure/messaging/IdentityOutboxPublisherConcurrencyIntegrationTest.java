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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@SuppressWarnings("unchecked")
class IdentityOutboxPublisherConcurrencyIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void secondPublisherSkipsAnEventClaimedByAnotherReplica() throws Exception {
        outboxRepository.deleteAll();
        OutboxEventEntity event = OutboxEventEntity.create(
                "User",
                UUID.randomUUID().toString(),
                "UserRegistered.v1",
                "agricore.identity.events",
                "{\"eventType\":\"UserRegistered.v1\"}"
        );
        outboxRepository.saveAndFlush(event);

        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> firstResult = new CompletableFuture<>();
        KafkaTemplate<String, String> firstKafka = mock(KafkaTemplate.class);
        when(firstKafka.send(event.getTopic(), event.getId().toString(), event.getPayload()))
                .thenAnswer(invocation -> {
                    firstSendStarted.countDown();
                    return firstResult;
                });
        KafkaTemplate<String, String> secondKafka = mock(KafkaTemplate.class);
        OutboxPublisher firstPublisher = publisher(firstKafka);
        OutboxPublisher secondPublisher = publisher(secondKafka);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(firstPublisher::publishPending);
            assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> secondRun = executor.submit(secondPublisher::publishPending);
            secondRun.get(2, TimeUnit.SECONDS);

            verify(secondKafka, never())
                    .send(event.getTopic(), event.getId().toString(), event.getPayload());
            firstResult.complete(mock(SendResult.class));
            firstRun.get(5, TimeUnit.SECONDS);
            verify(firstKafka).send(event.getTopic(), event.getId().toString(), event.getPayload());
        } finally {
            firstResult.complete(mock(SendResult.class));
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCannotLetTheOldClaimCompleteOverTheNewClaim() throws Exception {
        outboxRepository.deleteAll();
        OutboxEventEntity event = OutboxEventEntity.create(
                "User",
                UUID.randomUUID().toString(),
                "UserRegistered.v1",
                "agricore.identity.events",
                "{\"eventType\":\"UserRegistered.v1\"}"
        );
        outboxRepository.saveAndFlush(event);
        OutboxPublicationStore store = new OutboxPublicationStore(outboxRepository, transactionManager);

        OutboxPublicationStore.ClaimedEvent oldClaim =
                store.claim(event.getId(), UUID.randomUUID(), 50);
        Thread.sleep(100);
        OutboxPublicationStore.ClaimedEvent newClaim =
                store.claim(event.getId(), UUID.randomUUID(), 30_000);

        assertThat(oldClaim).isNotNull();
        assertThat(newClaim).isNotNull();
        assertThat(store.complete(oldClaim)).isFalse();
        assertThat(store.complete(newClaim)).isTrue();
        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    private OutboxPublisher publisher(KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxPublisher(
                new OutboxPublicationStore(outboxRepository, transactionManager),
                kafkaTemplate,
                5_000,
                30_000,
                new OutboxRetryProperties(100, 100, 10, false)
        );
    }
}
