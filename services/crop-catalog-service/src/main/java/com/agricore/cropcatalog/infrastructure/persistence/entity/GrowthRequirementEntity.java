package com.agricore.cropcatalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crop_growth_requirements")
public class GrowthRequirementEntity {

    @Id
    @Column(name = "crop_id")
    private UUID cropId;

    @Column(name = "irrigation_interval_days_min", nullable = false)
    private int irrigationIntervalDaysMin;

    @Column(name = "irrigation_interval_days_max", nullable = false)
    private int irrigationIntervalDaysMax;

    @Column(name = "fertilization_interval_days_min", nullable = false)
    private int fertilizationIntervalDaysMin;

    @Column(name = "fertilization_interval_days_max", nullable = false)
    private int fertilizationIntervalDaysMax;

    @Column(name = "water_requirement_mm_per_week", nullable = false, precision = 8, scale = 2)
    private BigDecimal waterRequirementMmPerWeek;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    public UUID getCropId() { return cropId; }
    public int getIrrigationIntervalDaysMin() { return irrigationIntervalDaysMin; }
    public int getIrrigationIntervalDaysMax() { return irrigationIntervalDaysMax; }
    public int getFertilizationIntervalDaysMin() { return fertilizationIntervalDaysMin; }
    public int getFertilizationIntervalDaysMax() { return fertilizationIntervalDaysMax; }
    public BigDecimal getWaterRequirementMmPerWeek() { return waterRequirementMmPerWeek; }
    public String getNotes() { return notes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public String getUpdatedBy() { return updatedBy; }

    public static GrowthRequirementEntity create(UUID cropId) {
        GrowthRequirementEntity requirement = new GrowthRequirementEntity();
        requirement.cropId = cropId;
        return requirement;
    }

    public void update(
            int irrigationMin,
            int irrigationMax,
            int fertilizationMin,
            int fertilizationMax,
            BigDecimal waterRequirement,
            String notes,
            String actor,
            Instant timestamp
    ) {
        this.irrigationIntervalDaysMin = irrigationMin;
        this.irrigationIntervalDaysMax = irrigationMax;
        this.fertilizationIntervalDaysMin = fertilizationMin;
        this.fertilizationIntervalDaysMax = fertilizationMax;
        this.waterRequirementMmPerWeek = waterRequirement;
        this.notes = notes;
        this.updatedBy = actor;
        this.updatedAt = timestamp;
    }
}
