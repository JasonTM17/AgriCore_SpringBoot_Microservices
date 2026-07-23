package com.agricore.inventory.infrastructure.messaging;

import com.agricore.inventory.application.service.InventoryApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.UUID;

import static org.mockito.Mockito.mock;

@Configuration(proxyBeanMethods = false)
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
        return new ObjectMapper();
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("inventory-retry-test-");
        return scheduler;
    }

    @Bean
    InventoryApplicationService inventoryApplicationService() {
        return mock(InventoryApplicationService.class);
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
