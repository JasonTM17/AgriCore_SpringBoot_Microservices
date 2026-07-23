package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.UUID;

import static org.mockito.Mockito.mock;

@Configuration(proxyBeanMethods = false)
@Profile("kafka-direct-dlt")
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
@Import({
        KafkaConsumerErrorConfig.class,
        HarvestCompletedKafkaListener.class,
        HarvestCompletedEventParser.class
})
class KafkaDirectDltTestConfiguration {

    @Bean
    NewTopic harvestEventsTopic() {
        return TopicBuilder.name("agricore.harvest.events")
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("traceability-retry-test-");
        return scheduler;
    }

    @Bean
    TraceabilityApplicationService traceabilityApplicationService() {
        return mock(TraceabilityApplicationService.class);
    }

    static String validEvent() {
        UUID harvestId = UUID.randomUUID();
        return """
                {
                  "eventId":"%s",
                  "eventType":"HarvestCompleted.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-23T00:00:00Z",
                  "producer":"harvest-service",
                  "payload":{
                    "harvestId":"%s",
                    "harvestBatchId":"%s",
                    "cropCycleId":"%s",
                    "plotId":"%s",
                    "warehouseId":"%s",
                    "productCode":"ROBUSTA",
                    "grossWeightKg":101,
                    "netWeightKg":100,
                    "qualityGrade":"GRADE_A",
                    "harvestDate":"2026-07-23",
                    "productName":"Robusta"
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                harvestId,
                harvestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
