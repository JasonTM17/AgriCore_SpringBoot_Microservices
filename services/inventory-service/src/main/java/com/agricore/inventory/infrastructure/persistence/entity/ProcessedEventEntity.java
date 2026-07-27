package com.agricore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    public static ProcessedEventEntity of(
            String eventId,
            String consumerName,
            UUID farmId,
            UUID warehouseId
    ) {
        ProcessedEventEntity e = new ProcessedEventEntity();
        e.eventId = eventId;
        e.consumerName = consumerName;
        e.processedAt = Instant.now();
        e.farmId = farmId;
        e.warehouseId = warehouseId;
        return e;
    }

    public String getEventId() { return eventId; }
    public String getConsumerName() { return consumerName; }
    public Instant getProcessedAt() { return processedAt; }
    public UUID getFarmId() { return farmId; }
    public UUID getWarehouseId() { return warehouseId; }

    public static class Pk implements Serializable {
        private String eventId;
        private String consumerName;

        public Pk() {
        }

        public Pk(String eventId, String consumerName) {
            this.eventId = eventId;
            this.consumerName = consumerName;
        }

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
