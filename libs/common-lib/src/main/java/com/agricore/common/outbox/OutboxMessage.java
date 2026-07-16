package com.agricore.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Portable outbox row representation (mapped by each service's JPA entity).
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String payloadJson,
        Instant createdAt,
        Instant publishedAt,
        int publishAttempts,
        String lastError
) {
    public boolean isPublished() {
        return publishedAt != null;
    }
}
