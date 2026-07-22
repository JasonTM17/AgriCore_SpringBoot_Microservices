package com.agricore.notification.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableKafkaRetryTopic
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerErrorConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry
    ) {
        Counter dlqAttempts = Counter.builder("agricore.kafka.dlq.attempts")
                .description("Kafka records handed to dead-letter recovery")
                .tag("consumer", "notification-service")
                .register(meterRegistry);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    dlqAttempts.increment();
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(8_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
