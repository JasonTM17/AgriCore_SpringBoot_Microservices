package com.agricore.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventEnvelopeTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void create_populatesRequiredFields() {
        ObjectNode payload = mapper.createObjectNode().put("farmId", "abc");

        DomainEventEnvelope event = DomainEventEnvelope.create(
                EventTypes.FARM_CREATED,
                1,
                "farm-service",
                "corr-1",
                null,
                "trace-1",
                payload
        );

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo(EventTypes.FARM_CREATED);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.producer()).isEqualTo("farm-service");
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.payload().get("farmId").asText()).isEqualTo("abc");
    }
}
