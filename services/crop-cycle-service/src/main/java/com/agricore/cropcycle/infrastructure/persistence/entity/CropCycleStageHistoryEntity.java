package com.agricore.cropcycle.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crop_cycle_stage_history")
public class CropCycleStageHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;

    @Column(name = "previous_stage", length = 40)
    private String previousStage;

    @Column(nullable = false, length = 40)
    private String stage;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "cycle_version", nullable = false)
    private long cycleVersion;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }
    public String getPreviousStage() { return previousStage; }
    public void setPreviousStage(String previousStage) { this.previousStage = previousStage; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public long getCycleVersion() { return cycleVersion; }
    public void setCycleVersion(long cycleVersion) { this.cycleVersion = cycleVersion; }
}
