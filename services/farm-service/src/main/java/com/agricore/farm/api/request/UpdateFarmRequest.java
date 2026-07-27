package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class UpdateFarmRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @Size(min = 1, max = 200)
    private String name;
    private boolean namePresent;

    @Size(max = 500)
    private String address;
    private boolean addressPresent;

    @Size(max = 120)
    private String province;
    private boolean provincePresent;

    @DecimalMin("0.0")
    private BigDecimal totalAreaHa;
    private boolean totalAreaHaPresent;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;
    private boolean latitudePresent;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;
    private boolean longitudePresent;

    @Pattern(regexp = "(?i)ACTIVE|INACTIVE|MAINTENANCE")
    private String status;
    private boolean statusPresent;

    private UUID enterpriseId;
    private boolean enterpriseIdPresent;

    public Long version() { return version; }
    public String name() { return name; }
    public boolean namePresent() { return namePresent; }
    public String address() { return address; }
    public boolean addressPresent() { return addressPresent; }
    public String province() { return province; }
    public boolean provincePresent() { return provincePresent; }
    public BigDecimal totalAreaHa() { return totalAreaHa; }
    public boolean totalAreaHaPresent() { return totalAreaHaPresent; }
    public Double latitude() { return latitude; }
    public boolean latitudePresent() { return latitudePresent; }
    public Double longitude() { return longitude; }
    public boolean longitudePresent() { return longitudePresent; }
    public String status() { return status; }
    public boolean statusPresent() { return statusPresent; }
    public UUID enterpriseId() { return enterpriseId; }
    public boolean enterpriseIdPresent() { return enterpriseIdPresent; }

    public void setVersion(Long version) { this.version = version; }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    @JsonSetter("address")
    public void setAddress(String address) {
        this.address = address;
        this.addressPresent = true;
    }

    @JsonSetter("province")
    public void setProvince(String province) {
        this.province = province;
        this.provincePresent = true;
    }

    @JsonSetter("totalAreaHa")
    public void setTotalAreaHa(BigDecimal totalAreaHa) {
        this.totalAreaHa = totalAreaHa;
        this.totalAreaHaPresent = true;
    }

    @JsonSetter("latitude")
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
        this.latitudePresent = true;
    }

    @JsonSetter("longitude")
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
        this.longitudePresent = true;
    }

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    @JsonSetter("enterpriseId")
    public void setEnterpriseId(UUID enterpriseId) {
        this.enterpriseId = enterpriseId;
        this.enterpriseIdPresent = true;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown farm field: " + field);
    }
}
