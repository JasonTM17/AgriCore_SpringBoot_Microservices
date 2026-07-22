package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Builds local traceability read model from HarvestCompleted.v1 (idempotent).
 */
@Component
@ConditionalOnProperty(name = "agricore.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class HarvestCompletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(HarvestCompletedKafkaListener.class);

    private final TraceabilityApplicationService service;
    private final HarvestCompletedEventParser eventParser;

    public HarvestCompletedKafkaListener(
            TraceabilityApplicationService service,
            HarvestCompletedEventParser eventParser
    ) {
        this.service = service;
        this.eventParser = eventParser;
    }

    @KafkaListener(
            topics = "${agricore.kafka.topics.harvest-events:agricore.harvest.events}",
            groupId = "${agricore.kafka.consumer.group-id:traceability-service}"
    )
    public void onMessage(String raw) {
        Optional<CreateTraceabilityRequest> request = parse(raw);
        if (request.isEmpty()) {
            return;
        }
        try {
            CreateTraceabilityRequest parsed = request.orElseThrow();
            service.createFromHarvest(parsed);
            log.info("Traceability projection updated for eventId={}", parsed.eventId());
        } catch (Exception ex) {
            log.error("Failed to process harvest event for traceability: {}", ex.getMessage());
            throw new IllegalStateException("Traceability harvest event processing failed", ex);
        }
    }

    private Optional<CreateTraceabilityRequest> parse(String raw) {
        try {
            return eventParser.parse(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting invalid harvest event: {}", ex.getMessage());
            throw ex;
        }
    }
}
