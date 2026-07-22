package com.agricore.iot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class DeviceEntity {
    @Id
    private UUID id;
    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;
    @Column(name = "plot_id", nullable = false)
    private UUID plotId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getVersion() { return version; }
}
