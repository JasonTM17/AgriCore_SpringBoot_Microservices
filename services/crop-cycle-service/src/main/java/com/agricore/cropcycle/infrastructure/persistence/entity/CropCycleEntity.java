package com.agricore.cropcycle.infrastructure.persistence.entity;

import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.domain.model.CycleStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crop_cycles")
public class CropCycleEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(name = "crop_variety_id")
    private UUID cropVarietyId;

    @Column(name = "planned_start_date", nullable = false)
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CycleStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CycleStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public UUID getCropId() { return cropId; }
    public void setCropId(UUID cropId) { this.cropId = cropId; }
    public UUID getCropVarietyId() { return cropVarietyId; }
    public void setCropVarietyId(UUID cropVarietyId) { this.cropVarietyId = cropVarietyId; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDate plannedEndDate) { this.plannedEndDate = plannedEndDate; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDate actualStartDate) { this.actualStartDate = actualStartDate; }
    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }
    public CycleStage getStage() { return stage; }
    public void setStage(CycleStage stage) { this.stage = stage; }
    public CycleStatus getStatus() { return status; }
    public void setStatus(CycleStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
