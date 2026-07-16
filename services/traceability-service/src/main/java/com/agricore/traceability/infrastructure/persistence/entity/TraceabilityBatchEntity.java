package com.agricore.traceability.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "traceability_batches")
public class TraceabilityBatchEntity {
    @Id
    private UUID id;
    @Column(name = "traceability_code", nullable = false, length = 64)
    private String traceabilityCode;
    @Column(name = "harvest_batch_id", nullable = false)
    private UUID harvestBatchId;
    @Column(name = "crop_cycle_id")
    private UUID cropCycleId;
    @Column(name = "plot_id")
    private UUID plotId;
    @Column(name = "farm_name", length = 200)
    private String farmName;
    @Column(name = "plot_code", length = 64)
    private String plotCode;
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;
    @Column(name = "variety_name", length = 200)
    private String varietyName;
    @Column(name = "planting_date")
    private LocalDate plantingDate;
    @Column(name = "harvest_date", nullable = false)
    private LocalDate harvestDate;
    @Column(name = "quality_grade", length = 32)
    private String qualityGrade;
    @Column(name = "net_weight_kg", precision = 14, scale = 3)
    private BigDecimal netWeightKg;
    @Column(name = "care_summary", columnDefinition = "TEXT")
    private String careSummary;
    @Column(name = "qr_url", nullable = false, length = 500)
    private String qrUrl;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTraceabilityCode() { return traceabilityCode; }
    public void setTraceabilityCode(String traceabilityCode) { this.traceabilityCode = traceabilityCode; }
    public UUID getHarvestBatchId() { return harvestBatchId; }
    public void setHarvestBatchId(UUID harvestBatchId) { this.harvestBatchId = harvestBatchId; }
    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public String getFarmName() { return farmName; }
    public void setFarmName(String farmName) { this.farmName = farmName; }
    public String getPlotCode() { return plotCode; }
    public void setPlotCode(String plotCode) { this.plotCode = plotCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getVarietyName() { return varietyName; }
    public void setVarietyName(String varietyName) { this.varietyName = varietyName; }
    public LocalDate getPlantingDate() { return plantingDate; }
    public void setPlantingDate(LocalDate plantingDate) { this.plantingDate = plantingDate; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public String getQualityGrade() { return qualityGrade; }
    public void setQualityGrade(String qualityGrade) { this.qualityGrade = qualityGrade; }
    public BigDecimal getNetWeightKg() { return netWeightKg; }
    public void setNetWeightKg(BigDecimal netWeightKg) { this.netWeightKg = netWeightKg; }
    public String getCareSummary() { return careSummary; }
    public void setCareSummary(String careSummary) { this.careSummary = careSummary; }
    public String getQrUrl() { return qrUrl; }
    public void setQrUrl(String qrUrl) { this.qrUrl = qrUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
