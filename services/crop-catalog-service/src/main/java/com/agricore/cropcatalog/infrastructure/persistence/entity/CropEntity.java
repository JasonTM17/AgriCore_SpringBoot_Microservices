package com.agricore.cropcatalog.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crops")
public class CropEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "scientific_name", length = 200)
    private String scientificName;
    @Column(nullable = false, length = 100)
    private String category;
    @Column(name = "growth_days_min")
    private Integer growthDaysMin;
    @Column(name = "growth_days_max")
    private Integer growthDaysMax;
    @Column(name = "temp_min_c")
    private BigDecimal tempMinC;
    @Column(name = "temp_max_c")
    private BigDecimal tempMaxC;
    @Column(name = "humidity_min_pct")
    private BigDecimal humidityMinPct;
    @Column(name = "humidity_max_pct")
    private BigDecimal humidityMaxPct;
    @Column(name = "ph_min")
    private BigDecimal phMin;
    @Column(name = "ph_max")
    private BigDecimal phMax;
    @Column(name = "expected_yield_per_ha")
    private BigDecimal expectedYieldPerHa;
    @Column(name = "yield_unit", length = 32)
    private String yieldUnit;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getScientificName() { return scientificName; }
    public String getCategory() { return category; }
    public Integer getGrowthDaysMin() { return growthDaysMin; }
    public Integer getGrowthDaysMax() { return growthDaysMax; }
    public BigDecimal getTempMinC() { return tempMinC; }
    public BigDecimal getTempMaxC() { return tempMaxC; }
    public BigDecimal getHumidityMinPct() { return humidityMinPct; }
    public BigDecimal getHumidityMaxPct() { return humidityMaxPct; }
    public BigDecimal getPhMin() { return phMin; }
    public BigDecimal getPhMax() { return phMax; }
    public BigDecimal getExpectedYieldPerHa() { return expectedYieldPerHa; }
    public String getYieldUnit() { return yieldUnit; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
