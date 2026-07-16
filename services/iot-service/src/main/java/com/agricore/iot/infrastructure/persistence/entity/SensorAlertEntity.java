package com.agricore.iot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_alerts")
public class SensorAlertEntity {
    @Id
    private UUID id;
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;
    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;
    @Column(name = "metric_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal metricValue;
    @Column(nullable = false, length = 32)
    private String severity;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;
    @Column(nullable = false, length = 128)
    private String fingerprint;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(int ruleVersion) { this.ruleVersion = ruleVersion; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
