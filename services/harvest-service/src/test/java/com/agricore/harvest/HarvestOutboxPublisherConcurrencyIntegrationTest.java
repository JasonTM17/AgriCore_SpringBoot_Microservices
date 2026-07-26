package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.infrastructure.messaging.OutboxPublisher;
import com.agricore.harvest.infrastructure.messaging.OutboxRetryProperties;
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
@SuppressWarnings("unchecked")
class HarvestOutboxPublisherConcurrencyIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void secondPublisher_skipsRowLockedByInFlightPublisher() throws Exception {
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

        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> firstResult = new CompletableFuture<>();
        KafkaTemplate<String, String> firstKafka = mock(KafkaTemplate.class);
        when(firstKafka.send(event.getTopic(), eventId.toString(), payload)).thenAnswer(ignored -> {
            firstSendStarted.countDown();
            return firstResult;
        });
        KafkaTemplate<String, String> secondKafka = mock(KafkaTemplate.class);
        OutboxPublisher firstPublisher = publisher(firstKafka, 5_000);
        OutboxPublisher secondPublisher = publisher(secondKafka, 5_000);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(firstPublisher::publishPending);
            assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> secondRun = executor.submit(secondPublisher::publishPending);
            secondRun.get(1, TimeUnit.SECONDS);

            verify(secondKafka, never()).send(event.getTopic(), eventId.toString(), payload);
            firstResult.complete(mock(SendResult.class));
            firstRun.get(5, TimeUnit.SECONDS);
            verify(firstKafka).send(event.getTopic(), eventId.toString(), payload);
        } finally {
            firstResult.complete(mock(SendResult.class));
            executor.shutdownNow();
        }
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
}
