package com.agricore.harvest.infrastructure.persistence.entity;

import com.agricore.harvest.domain.model.HarvestStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harvest_batches")
public class HarvestBatchEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;
    @Column(name = "plot_id", nullable = false)
    private UUID plotId;
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;
    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;
    @Column(name = "gross_weight_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal grossWeightKg;
    @Column(name = "net_weight_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal netWeightKg;
    @Column(name = "quality_grade", nullable = false, length = 32)
    private String qualityGrade;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HarvestStatus status;
    @Column(name = "harvested_at", nullable = false)
    private Instant harvestedAt;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "last_outbox_event_id")
    private UUID lastOutboxEventId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public BigDecimal getGrossWeightKg() { return grossWeightKg; }
    public void setGrossWeightKg(BigDecimal grossWeightKg) { this.grossWeightKg = grossWeightKg; }
    public BigDecimal getNetWeightKg() { return netWeightKg; }
    public void setNetWeightKg(BigDecimal netWeightKg) { this.netWeightKg = netWeightKg; }
    public String getQualityGrade() { return qualityGrade; }
    public void setQualityGrade(String qualityGrade) { this.qualityGrade = qualityGrade; }
    public HarvestStatus getStatus() { return status; }
    public void setStatus(HarvestStatus status) { this.status = status; }
    public Instant getHarvestedAt() { return harvestedAt; }
    public void setHarvestedAt(Instant harvestedAt) { this.harvestedAt = harvestedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getLastOutboxEventId() { return lastOutboxEventId; }
    public void setLastOutboxEventId(UUID lastOutboxEventId) { this.lastOutboxEventId = lastOutboxEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
