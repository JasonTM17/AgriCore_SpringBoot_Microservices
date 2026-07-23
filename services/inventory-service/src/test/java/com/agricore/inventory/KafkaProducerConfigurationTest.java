package com.agricore.inventory;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigurationTest {

    @Test
    void keepsEffectiveProducerAndOutboxTimeoutsAligned() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("producer-timeout-test", Map.of(
                "KAFKA_PRODUCER_MAX_BLOCK_MS", "5000",
                "KAFKA_PRODUCER_REQUEST_TIMEOUT_MS", "5000",
                "KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS", "10000",
                "OUTBOX_PUBLISHER_SEND_TIMEOUT_MS", "10000"
        )));
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        KafkaProperties kafkaProperties = Binder.get(environment)
                .bind("spring.kafka", KafkaProperties.class)
                .get();
        Map<String, Object> effectiveProperties = kafkaProperties.buildProducerProperties(null);

        long maxBlock = longValue(effectiveProperties, ProducerConfig.MAX_BLOCK_MS_CONFIG);
        long requestTimeout = longValue(effectiveProperties, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        long deliveryTimeout = longValue(effectiveProperties, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
        long outboxSendTimeout = environment.getRequiredProperty(
                "agricore.outbox.publisher.send-timeout-ms",
                Long.class
        );

        assertThat(maxBlock).isEqualTo(5000);
        assertThat(requestTimeout).isEqualTo(5000);
        assertThat(deliveryTimeout).isEqualTo(10000);
        assertThat(requestTimeout).isLessThan(deliveryTimeout);
        assertThat(maxBlock).isLessThanOrEqualTo(outboxSendTimeout);
        assertThat(deliveryTimeout).isLessThanOrEqualTo(outboxSendTimeout);
    }

    private static long longValue(Map<String, Object> properties, String name) {
        return Long.parseLong(properties.get(name).toString());
    }
}
