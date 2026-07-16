package com.agricore.traceability.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Pk.class)
public class ProcessedEventEntity {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;
    @Id
    @Column(name = "consumer_name", length = 100)
    private String consumerName;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static ProcessedEventEntity of(String eventId, String consumerName) {
        ProcessedEventEntity e = new ProcessedEventEntity();
        e.eventId = eventId;
        e.consumerName = consumerName;
        e.processedAt = Instant.now();
        return e;
    }

    public static class Pk implements Serializable {
        private String eventId;
        private String consumerName;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(eventId, pk.eventId) && Objects.equals(consumerName, pk.consumerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumerName);
        }
    }
}
