package com.agricore.common.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code isPublished} is what the five polling publishers use to decide whether a row still needs
 * sending. Reading it the wrong way round would either replay every event or drop every event.
 */
class OutboxMessageTest {

    @Test
    void anUnsentRowIsNotPublished() {
        assertThat(message(null).isPublished()).isFalse();
    }

    @Test
    void aRowWithASendTimestampIsPublished() {
        assertThat(message(Instant.parse("2026-07-26T10:15:30Z")).isPublished()).isTrue();
    }

    /**
     * Publication is decided by the timestamp alone. A row that failed and was retried carries an
     * attempt count and an error message but must still be treated as unsent.
     */
    @Test
    void failedAttemptsDoNotMarkARowAsPublished() {
        OutboxMessage retried = new OutboxMessage(
                UUID.randomUUID(), "Farm", "farm-1", "FarmCreated.v1", "agricore.farm",
                "{}", Instant.parse("2026-07-26T10:00:00Z"), null, 7, "broker unreachable");

        assertThat(retried.isPublished()).isFalse();
    }

    private static OutboxMessage message(Instant publishedAt) {
        return new OutboxMessage(
                UUID.randomUUID(), "Farm", "farm-1", "FarmCreated.v1", "agricore.farm",
                "{}", Instant.parse("2026-07-26T10:00:00Z"), publishedAt, 0, null);
    }
}
