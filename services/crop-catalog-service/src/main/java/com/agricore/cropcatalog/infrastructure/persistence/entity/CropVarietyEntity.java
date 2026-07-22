package com.agricore.cropcatalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crop_varieties")
public class CropVarietyEntity {

    @Id
    private UUID id;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String origin;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getCropId() { return cropId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getOrigin() { return origin; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
