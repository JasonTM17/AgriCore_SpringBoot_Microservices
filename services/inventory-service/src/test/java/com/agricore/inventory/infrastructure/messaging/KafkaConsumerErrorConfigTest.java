package com.agricore.inventory.infrastructure.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerErrorConfigTest {

    @Test
    void declaresBoundedNonBlockingRetryTopicsWithContractFailuresExcluded() throws Exception {
        RetryableTopic retryableTopic = HarvestCompletedKafkaListener.class
                .getDeclaredMethod("onMessage", String.class)
                .getAnnotation(RetryableTopic.class);

        assertThat(retryableTopic).isNotNull();
        assertThat(retryableTopic.attempts()).isEqualTo("4");
        assertThat(retryableTopic.backoff().delay()).isEqualTo(1000);
        assertThat(retryableTopic.backoff().multiplier()).isEqualTo(2.0);
        assertThat(retryableTopic.backoff().maxDelay()).isEqualTo(4000);
        assertThat(retryableTopic.timeout()).isEqualTo("30000");
        assertThat(retryableTopic.dltTopicSuffix()).isEqualTo(".DLT");
        assertThat(retryableTopic.exclude()).containsExactly(IllegalArgumentException.class);
        assertThat(retryableTopic.autoCreateTopics()).isEqualTo("false");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void metersOnlyDltDestinationsOnTheRetryTopicRecoverer() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaConsumerErrorConfig config = new KafkaConsumerErrorConfig(meterRegistry);
        DeadLetterPublishingRecovererFactory factory = mock(DeadLetterPublishingRecovererFactory.class);
        ArgumentCaptor<DeadLetterPublishingRecovererFactory.DeadLetterPublisherCreator> creatorCaptor =
                ArgumentCaptor.forClass(DeadLetterPublishingRecovererFactory.DeadLetterPublisherCreator.class);

        config.configureDeadLetterPublishingContainerFactory().accept(factory);
        verify(factory).setDeadLetterPublisherCreator(creatorCaptor.capture());

        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>> templateResolver = ignored -> kafkaTemplate;
        AtomicReference<TopicPartition> destination = new AtomicReference<>(
                new TopicPartition("agricore.harvest.events-retry-1000", 2)
        );
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
                (record, exception) -> destination.get();
        DeadLetterPublishingRecoverer recoverer = creatorCaptor.getValue()
                .create(templateResolver, destinationResolver);
        recoverer.setVerifyPartition(false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "agricore.harvest.events",
                2,
                41L,
                "event-key",
                "invalid-envelope"
        );
        record.headers().add("correlation-id", "correlation-123".getBytes(StandardCharsets.UTF_8));

        recoverer.accept(record, null, new IllegalStateException("transient"));

        assertThat(meterRegistry.counter(
                "agricore.kafka.dlq.attempts",
                "consumer",
                "inventory-service"
        ).count()).isZero();

        destination.set(new TopicPartition("agricore.harvest.events.DLT", 2));
        recoverer.accept(record, null, new IllegalArgumentException("invalid contract"));

        assertThat(meterRegistry.counter(
                "agricore.kafka.dlq.attempts",
                "consumer",
                "inventory-service"
        ).count()).isEqualTo(1.0);
        ArgumentCaptor<ProducerRecord<Object, Object>> published = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(published.capture());
        ProducerRecord<Object, Object> dltRecord = published.getAllValues().get(1);
        assertThat(dltRecord.topic()).isEqualTo("agricore.harvest.events.DLT");
        assertThat(dltRecord.partition()).isEqualTo(2);
        assertThat(dltRecord.key()).isEqualTo("event-key");
        assertThat(dltRecord.value()).isEqualTo("invalid-envelope");
        assertThat(dltRecord.headers().lastHeader("correlation-id").value())
                .isEqualTo("correlation-123".getBytes(StandardCharsets.UTF_8));
    }
}
