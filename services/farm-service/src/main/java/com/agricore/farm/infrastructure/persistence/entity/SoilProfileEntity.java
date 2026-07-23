package com.agricore.farm.infrastructure.persistence.entity;

import com.agricore.farm.domain.model.SoilProfileStatus;
import com.agricore.farm.domain.model.SoilTexture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "soil_profiles")
public class SoilProfileEntity {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "sample_code", nullable = false, length = 64)
    private String sampleCode;

    @Column(name = "sampled_at", nullable = false)
    private LocalDate sampledAt;

    @Column(name = "sample_depth_cm", nullable = false, precision = 5, scale = 2)
    private BigDecimal sampleDepthCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SoilTexture texture;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ph;

    @Column(name = "organic_matter_percent", precision = 5, scale = 2)
    private BigDecimal organicMatterPercent;

    @Column(name = "nitrogen_mg_kg", precision = 10, scale = 2)
    private BigDecimal nitrogenMgKg;

    @Column(name = "phosphorus_mg_kg", precision = 10, scale = 2)
    private BigDecimal phosphorusMgKg;

    @Column(name = "potassium_mg_kg", precision = 10, scale = 2)
    private BigDecimal potassiumMgKg;

    @Column(name = "moisture_percent", precision = 5, scale = 2)
    private BigDecimal moisturePercent;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SoilProfileStatus status;

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
    public String getSampleCode() { return sampleCode; }
    public void setSampleCode(String sampleCode) { this.sampleCode = sampleCode; }
    public LocalDate getSampledAt() { return sampledAt; }
    public void setSampledAt(LocalDate sampledAt) { this.sampledAt = sampledAt; }
    public BigDecimal getSampleDepthCm() { return sampleDepthCm; }
    public void setSampleDepthCm(BigDecimal sampleDepthCm) { this.sampleDepthCm = sampleDepthCm; }
    public SoilTexture getTexture() { return texture; }
    public void setTexture(SoilTexture texture) { this.texture = texture; }
    public BigDecimal getPh() { return ph; }
    public void setPh(BigDecimal ph) { this.ph = ph; }
    public BigDecimal getOrganicMatterPercent() { return organicMatterPercent; }
    public void setOrganicMatterPercent(BigDecimal value) { this.organicMatterPercent = value; }
    public BigDecimal getNitrogenMgKg() { return nitrogenMgKg; }
    public void setNitrogenMgKg(BigDecimal value) { this.nitrogenMgKg = value; }
    public BigDecimal getPhosphorusMgKg() { return phosphorusMgKg; }
    public void setPhosphorusMgKg(BigDecimal value) { this.phosphorusMgKg = value; }
    public BigDecimal getPotassiumMgKg() { return potassiumMgKg; }
    public void setPotassiumMgKg(BigDecimal value) { this.potassiumMgKg = value; }
    public BigDecimal getMoisturePercent() { return moisturePercent; }
    public void setMoisturePercent(BigDecimal value) { this.moisturePercent = value; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public SoilProfileStatus getStatus() { return status; }
    public void setStatus(SoilProfileStatus status) { this.status = status; }
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
