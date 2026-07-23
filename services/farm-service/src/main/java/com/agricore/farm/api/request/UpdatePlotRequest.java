package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class UpdatePlotRequest {

    @Size(max = 200)
    private String name;

    @DecimalMin("0.0001")
    private BigDecimal areaInHectares;

    @Size(max = 100)
    private String soilType;

    @Pattern(regexp = "(?i)AVAILABLE|PREPARING|IN_USE|RESTING|MAINTENANCE|INACTIVE")
    private String status;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    private UUID areaId;
    private boolean areaIdPresent;

    public UpdatePlotRequest() {
    }

    public String name() { return name; }
    public BigDecimal areaInHectares() { return areaInHectares; }
    public String soilType() { return soilType; }
    public String status() { return status; }
    public Double latitude() { return latitude; }
    public Double longitude() { return longitude; }
    public UUID areaId() { return areaId; }
    public boolean areaIdPresent() { return areaIdPresent; }

    public void setName(String name) { this.name = name; }
    public void setAreaInHectares(BigDecimal areaInHectares) { this.areaInHectares = areaInHectares; }
    public void setSoilType(String soilType) { this.soilType = soilType; }
    public void setStatus(String status) { this.status = status; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    @JsonSetter("areaId")
    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
        this.areaIdPresent = true;
    }
}
