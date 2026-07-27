package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.application.service.TraceabilityApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(KafkaDirectDltTestConfiguration.class)
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "agricore.harvest.events",
                "agricore.harvest.events-retry-1000",
                "agricore.harvest.events-retry-2000",
                "agricore.harvest.events-retry-4000",
                "agricore.harvest.events.DLT"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "agricore.kafka.consumer.group-id=traceability-direct-dlt-test"
})
@ActiveProfiles("kafka-direct-dlt")
class KafkaDirectDltIntegrationTest {

    private static final String MAIN_TOPIC = "agricore.harvest.events";
    private static final String DLT_TOPIC = MAIN_TOPIC + ".DLT";
    private static final List<String> RETRY_TOPICS = List.of(
            MAIN_TOPIC + "-retry-1000",
            MAIN_TOPIC + "-retry-2000",
            MAIN_TOPIC + "-retry-4000"
    );

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private TraceabilityApplicationService traceabilityService;
    @Autowired
    private MeterRegistry meterRegistry;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void subscribeToDlt() {
        dltConsumer = consumer("traceability-dlt-verifier-" + UUID.randomUUID());
        dltConsumer.subscribe(List.of(DLT_TOPIC));
    }

    @AfterEach
    void closeConsumer() {
        dltConsumer.close();
    }

    @Test
    void invalidRecordsBypassRetriesWhileTransientFailuresRemainBounded() throws Exception {
        assertDirectDlt(0, 0, "malformed-key", "{not-json", "malformed-correlation");
        String wrongVersion = """
                {
                  "eventId":"%s",
                  "eventType":"HarvestCompleted.v2",
                  "eventVersion":2,
                  "occurredAt":"2026-07-23T00:00:00Z",
                  "producer":"harvest-service",
                  "payload":{}
                }
                """.formatted(UUID.randomUUID());
        assertDirectDlt(1, 0, "wrong-version-key", wrongVersion, "version-correlation");
        verifyNoInteractions(traceabilityService);

        doThrow(new IllegalStateException("database temporarily unavailable"))
                .when(traceabilityService)
                .createFromHarvest(any());
        assertDirectDlt(
                0,
                0,
                "transient-key",
                KafkaDirectDltTestConfiguration.validEvent(),
                "transient-correlation"
        );

        Consumer<String, String> retryConsumer = consumer("traceability-retry-verifier-" + UUID.randomUUID());
        try {
            retryConsumer.subscribe(RETRY_TOPICS);
            ConsumerRecords<String, String> retries =
                    KafkaTestUtils.getRecords(retryConsumer, Duration.ofSeconds(5), 3);
            assertThat(retries.count()).isEqualTo(3);
            assertThat(retries)
                    .extracting(ConsumerRecord::topic)
                    .containsExactlyInAnyOrderElementsOf(RETRY_TOPICS);
            assertThat(retries)
                    .extracting(ConsumerRecord::key)
                    .containsOnly("transient-key");
        } finally {
            retryConsumer.close();
        }
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(meterRegistry.counter(
                            "agricore.kafka.dlq.attempts",
                            "consumer",
                            "traceability-service"
                    ).count()).isEqualTo(3.0);
                    verify(traceabilityService, times(4)).createFromHarvest(any());
                });
    }

    private void assertDirectDlt(
            int sourcePartition,
            int expectedDltPartition,
            String key,
            String value,
            String correlationId
    ) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(MAIN_TOPIC, sourcePartition, key, value);
        record.headers().add("correlation-id", correlationId.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dlt =
                KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(15));

        assertThat(dlt.key()).isEqualTo(key);
        assertThat(dlt.value()).isEqualTo(value);
        assertThat(dlt.partition()).isEqualTo(expectedDltPartition);
        assertThat(headerValue(dlt, "correlation-id")).isEqualTo(correlationId);
        assertThat(dlt.headers().lastHeader(KafkaHeaders.ORIGINAL_TOPIC)).isNotNull();
        assertThat(integerHeaderValue(dlt, KafkaHeaders.ORIGINAL_PARTITION)).isEqualTo(sourcePartition);
        assertThat(dlt.headers().lastHeader(KafkaHeaders.EXCEPTION_FQCN)).isNotNull();
    }

    private Consumer<String, String> consumer(String groupId) {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(groupId, "false", embeddedKafka);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Integer integerHeaderValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : ByteBuffer.wrap(header.value()).getInt();
    }

}
