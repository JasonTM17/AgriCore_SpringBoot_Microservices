package com.agricore.farm.infrastructure.persistence.entity;

import com.agricore.farm.domain.model.IrrigationMethod;
import com.agricore.farm.domain.model.IrrigationZoneStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "irrigation_zones")
public class IrrigationZoneEntity {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IrrigationMethod method;

    @Column(name = "flow_rate_liters_per_minute", nullable = false, precision = 8, scale = 2)
    private BigDecimal flowRateLitersPerMinute;

    @Column(name = "target_moisture_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetMoisturePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IrrigationZoneStatus status;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public IrrigationMethod getMethod() { return method; }
    public void setMethod(IrrigationMethod method) { this.method = method; }
    public BigDecimal getFlowRateLitersPerMinute() { return flowRateLitersPerMinute; }
    public void setFlowRateLitersPerMinute(BigDecimal value) { this.flowRateLitersPerMinute = value; }
    public BigDecimal getTargetMoisturePercent() { return targetMoisturePercent; }
    public void setTargetMoisturePercent(BigDecimal value) { this.targetMoisturePercent = value; }
    public IrrigationZoneStatus getStatus() { return status; }
    public void setStatus(IrrigationZoneStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public long getVersion() { return version; }
}
