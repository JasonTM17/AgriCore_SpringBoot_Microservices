package com.agricore.inventory.infrastructure.messaging;

import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.application.service.InventoryApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka consumer for HarvestCompleted.v1 → stock-in (idempotent via processed_events).
 */
@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class HarvestCompletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestCompletedKafkaListener.class);

    private final InventoryApplicationService inventoryService;
    private final HarvestCompletedEventParser eventParser;

    public HarvestCompletedKafkaListener(
            InventoryApplicationService inventoryService,
            HarvestCompletedEventParser eventParser
    ) {
        this.inventoryService = inventoryService;
        this.eventParser = eventParser;
    }

    @RetryableTopic(
            attempts = "${AGRICORE_KAFKA_RETRY_ATTEMPTS:4}",
            backoff = @Backoff(
                    delayExpression = "${AGRICORE_KAFKA_RETRY_DELAY_MS:1000}",
                    multiplierExpression = "${AGRICORE_KAFKA_RETRY_MULTIPLIER:2}",
                    maxDelayExpression = "${AGRICORE_KAFKA_RETRY_MAX_DELAY_MS:8000}"
            ),
            timeout = "${AGRICORE_KAFKA_RETRY_TIMEOUT_MS:30000}",
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "${AGRICORE_KAFKA_RETRY_AUTO_CREATE_TOPICS:false}"
    )
    @KafkaListener(
            topics = "${agricore.kafka.topics.harvest-events:agricore.harvest.events}",
            groupId = "${agricore.kafka.consumer.group-id:inventory-service}"
    )
    public void onMessage(String raw) {
        var command = parse(raw);
        if (command.isEmpty()) {
            return;
        }
        HarvestCompletedCommand parsed = command.orElseThrow();
        try {
            inventoryService.processHarvestCompleted(parsed);
            log.info("Processed HarvestCompleted eventId={}", parsed.eventId());
        } catch (Exception ex) {
            log.error("Failed to process harvest event: {}", ex.getMessage());
            throw new IllegalStateException("Harvest event processing failed", ex);
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Inventory event routed to DLT topic={} partition={} offset={} exceptionType={}",
                record.topic(), record.partition(), record.offset(), exception.getClass().getSimpleName());
    }

    private Optional<HarvestCompletedCommand> parse(String raw) {
        try {
            return eventParser.parse(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting invalid harvest event: {}", ex.getMessage());
            throw ex;
        }
    }
}
