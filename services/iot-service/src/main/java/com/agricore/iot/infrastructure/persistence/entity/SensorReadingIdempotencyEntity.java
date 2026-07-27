package com.agricore.iot.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_reading_idempotency")
public class SensorReadingIdempotencyEntity {

    @Id
    @Column(name = "reading_id")
    private UUID readingId;

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

    public UUID getReadingId() { return readingId; }
    public UUID getDeviceId() { return deviceId; }
    public String getMetricType() { return metricType; }
    public BigDecimal getMetricValue() { return metricValue; }
    public String getUnit() { return unit; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
