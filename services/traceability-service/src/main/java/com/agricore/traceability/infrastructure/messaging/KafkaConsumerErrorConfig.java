package com.agricore.traceability.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.retrytopic.DeadLetterPublishingRecovererFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerErrorConfig extends RetryTopicConfigurationSupport {

    private static final String DLT_SUFFIX = ".DLT";
    private final Counter dltAttempts;

    KafkaConsumerErrorConfig(MeterRegistry meterRegistry) {
        this.dltAttempts = Counter.builder("agricore.kafka.dlq.attempts")
                .description("Kafka records handed to dead-letter recovery")
                .tag("consumer", "traceability-service")
                .register(meterRegistry);
    }

    @Override
    protected Consumer<DeadLetterPublishingRecovererFactory> configureDeadLetterPublishingContainerFactory() {
        return factory -> factory.setDeadLetterPublisherCreator(
                (templateResolver, destinationResolver) ->
                        new DltAttemptCountingRecoverer(templateResolver, destinationResolver, dltAttempts)
        );
    }

    private static final class DltAttemptCountingRecoverer extends DeadLetterPublishingRecoverer {

        private final Counter dltAttempts;

        private DltAttemptCountingRecoverer(
                Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>> templateResolver,
                BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver,
                Counter dltAttempts
        ) {
            super(templateResolver, destinationResolver);
            this.dltAttempts = dltAttempts;
        }

        @Override
        protected ProducerRecord<Object, Object> createProducerRecord(
                ConsumerRecord<?, ?> record,
                TopicPartition topicPartition,
                Headers headers,
                byte[] key,
                byte[] value
        ) {
            if (topicPartition.topic().endsWith(DLT_SUFFIX)) {
                dltAttempts.increment();
            }
            return super.createProducerRecord(record, topicPartition, headers, key, value);
        }
    }
}
