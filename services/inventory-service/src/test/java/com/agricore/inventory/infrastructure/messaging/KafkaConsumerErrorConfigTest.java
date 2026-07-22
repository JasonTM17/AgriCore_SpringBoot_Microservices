package com.agricore.inventory.infrastructure.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerErrorConfigTest {

    @Test
    void declaresBoundedNonBlockingRetryTopics() throws Exception {
        RetryableTopic retryableTopic = HarvestCompletedKafkaListener.class
                .getDeclaredMethod("onMessage", String.class)
                .getAnnotation(RetryableTopic.class);

        assertThat(retryableTopic).isNotNull();
        assertThat(retryableTopic.attempts()).isEqualTo("${AGRICORE_KAFKA_RETRY_ATTEMPTS:4}");
        assertThat(retryableTopic.dltTopicSuffix()).isEqualTo(".DLT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesInvalidContractsDirectlyToTheSamePartitionDlt() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DefaultErrorHandler handler = new KafkaConsumerErrorConfig()
                .kafkaErrorHandler(kafkaTemplate, meterRegistry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "agricore.harvest.events",
                2,
                41L,
                "event-key",
                "invalid-envelope"
        );
        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.partitionsFor("agricore.harvest.events.DLT")).thenReturn(List.of(
                new PartitionInfo("agricore.harvest.events.DLT", 2, null, new Node[0], new Node[0])
        ));
        when(consumer.partitionsFor(eq("agricore.harvest.events.DLT"), any(Duration.class))).thenReturn(List.of(
                new PartitionInfo("agricore.harvest.events.DLT", 2, null, new Node[0], new Node[0])
        ));

        boolean recovered = handler.handleOne(
                new ListenerExecutionFailedException(
                        "listener failed",
                        new IllegalArgumentException("invalid contract")
                ),
                record,
                consumer,
                mock(MessageListenerContainer.class)
        );

        assertThat(recovered).isTrue();
        ArgumentCaptor<ProducerRecord<String, String>> published = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(published.capture());
        assertThat(published.getValue().topic()).isEqualTo("agricore.harvest.events.DLT");
        assertThat(published.getValue().partition()).isEqualTo(2);
        assertThat(meterRegistry.counter(
                "agricore.kafka.dlq.attempts",
                "consumer",
                "inventory-service"
        ).count()).isEqualTo(1.0);
    }
}
