package com.agricore.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical Kafka event envelope for all AgriCore domain events.
 *
 * @see contracts/event-schemas/DomainEventEnvelope.v1.json
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String traceId,
        String correlationId,
        String causationId,
        String producer,
        JsonNode payload
) {
    public static DomainEventEnvelope create(
            String eventType,
            int eventVersion,
            String producer,
            String correlationId,
            String causationId,
            String traceId,
            JsonNode payload
    ) {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Instant.now(),
                traceId,
                correlationId,
                causationId,
                producer,
                payload
        );
    }
}
