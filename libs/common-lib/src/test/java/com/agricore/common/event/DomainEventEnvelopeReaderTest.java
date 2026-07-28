package com.agricore.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventEnvelopeReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void rejectsAnAbbreviatedEventId() {
        assertThatThrownBy(() -> DomainEventEnvelopeReader.read(objectMapper, envelope("1-1-1-1-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId must be a UUID");
    }

    @Test
    void acceptsAnUppercaseCanonicalEventId() {
        DomainEventEnvelopeReader.Envelope envelope = DomainEventEnvelopeReader.read(
                objectMapper,
                envelope("ABCDEFAB-CDEF-CDEF-CDEF-ABCDEFABCDEF")
        );

        assertThat(envelope.eventId().toString()).isEqualTo("abcdefab-cdef-cdef-cdef-abcdefabcdef");
    }

    private static String envelope(String eventId) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"TestEvent.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-28T08:00:00Z",
                  "producer":"test-service",
                  "payload":{}
                }
                """.formatted(eventId);
    }
}
