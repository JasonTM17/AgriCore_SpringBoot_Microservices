package com.agricore.cropcatalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_recommendations")
public class CareRecommendationEntity {

    @Id
    private UUID id;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "growth_stage", length = 64)
    private String growthStage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getCropId() { return cropId; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getGrowthStage() { return growthStage; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public static CareRecommendationEntity create(UUID id, UUID cropId, Instant createdAt) {
        CareRecommendationEntity recommendation = new CareRecommendationEntity();
        recommendation.id = id;
        recommendation.cropId = cropId;
        recommendation.createdAt = createdAt;
        return recommendation;
    }

    public void update(
            String category,
            String title,
            String description,
            String growthStage,
            int sortOrder
    ) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.growthStage = growthStage;
        this.sortOrder = sortOrder;
    }
}
