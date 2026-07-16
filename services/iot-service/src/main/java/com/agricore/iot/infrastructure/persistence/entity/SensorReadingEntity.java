package com.agricore.iot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_readings")
public class SensorReadingEntity {
    @Id
    private UUID id;
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;
    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;
    @Column(name = "metric_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal metricValue;
    @Column(nullable = false, length = 16)
    private String unit;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
