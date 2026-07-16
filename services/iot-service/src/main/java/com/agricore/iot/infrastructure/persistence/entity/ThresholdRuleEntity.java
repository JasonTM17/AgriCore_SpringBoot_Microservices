package com.agricore.iot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "threshold_rules")
public class ThresholdRuleEntity {
    @Id
    private UUID id;
    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;
    @Column(name = "min_value", precision = 14, scale = 4)
    private BigDecimal minValue;
    @Column(name = "max_value", precision = 14, scale = 4)
    private BigDecimal maxValue;
    @Column(nullable = false, length = 32)
    private String severity;
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public String getMetricType() { return metricType; }
    public BigDecimal getMinValue() { return minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public String getSeverity() { return severity; }
    public int getRuleVersion() { return ruleVersion; }
    public boolean isActive() { return active; }
}
