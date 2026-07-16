package com.agricore.traceability.infrastructure.messaging;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerErrorConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    String dlt = record.topic() + ".DLT";
                    log.error("Traceability DLT topic={} offset={} cause={}", dlt, record.offset(), ex.getMessage());
                    return new TopicPartition(dlt, record.partition());
                }
        );
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(8_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
