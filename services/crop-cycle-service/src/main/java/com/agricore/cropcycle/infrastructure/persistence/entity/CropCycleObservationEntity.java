package com.agricore.cropcycle.infrastructure.persistence.entity;

import com.agricore.cropcycle.domain.model.ObservationCategory;
import com.agricore.cropcycle.domain.model.ObservationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crop_cycle_observations")
public class CropCycleObservationEntity {

    @Id
    private UUID id;

    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ObservationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ObservationSeverity severity;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "recorded_by", nullable = false, length = 255)
    private String recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }
    public ObservationCategory getCategory() { return category; }
    public void setCategory(ObservationCategory category) { this.category = category; }
    public ObservationSeverity getSeverity() { return severity; }
    public void setSeverity(ObservationSeverity severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
