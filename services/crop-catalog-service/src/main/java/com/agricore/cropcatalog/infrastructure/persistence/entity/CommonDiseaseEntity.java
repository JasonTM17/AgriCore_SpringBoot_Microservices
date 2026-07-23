package com.agricore.cropcatalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "common_diseases")
public class CommonDiseaseEntity {

    @Id
    private UUID id;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String symptoms;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prevention;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String treatment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getCropId() { return cropId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymptoms() { return symptoms; }
    public String getPrevention() { return prevention; }
    public String getTreatment() { return treatment; }
    public Instant getCreatedAt() { return createdAt; }

    public static CommonDiseaseEntity create(UUID id, UUID cropId, Instant createdAt) {
        CommonDiseaseEntity disease = new CommonDiseaseEntity();
        disease.id = id;
        disease.cropId = cropId;
        disease.createdAt = createdAt;
        return disease;
    }

    public void update(String code, String name, String symptoms, String prevention, String treatment) {
        this.code = code;
        this.name = name;
        this.symptoms = symptoms;
        this.prevention = prevention;
        this.treatment = treatment;
    }
}
