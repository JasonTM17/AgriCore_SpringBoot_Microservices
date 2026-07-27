package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpdateEnterpriseRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @Size(min = 1, max = 200)
    private String name;
    private boolean namePresent;

    @Size(max = 250)
    private String legalName;
    private boolean legalNamePresent;

    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9.-]{0,63}")
    private String taxCode;
    private boolean taxCodePresent;

    @Size(max = 500)
    private String address;
    private boolean addressPresent;

    @Size(max = 120)
    private String province;
    private boolean provincePresent;

    @Pattern(regexp = "(?i)ACTIVE|INACTIVE")
    private String status;
    private boolean statusPresent;

    public Long version() { return version; }
    public String name() { return name; }
    public boolean namePresent() { return namePresent; }
    public String legalName() { return legalName; }
    public boolean legalNamePresent() { return legalNamePresent; }
    public String taxCode() { return taxCode; }
    public boolean taxCodePresent() { return taxCodePresent; }
    public String address() { return address; }
    public boolean addressPresent() { return addressPresent; }
    public String province() { return province; }
    public boolean provincePresent() { return provincePresent; }
    public String status() { return status; }
    public boolean statusPresent() { return statusPresent; }

    public void setVersion(Long version) { this.version = version; }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    @JsonSetter("legalName")
    public void setLegalName(String legalName) {
        this.legalName = legalName;
        this.legalNamePresent = true;
    }

    @JsonSetter("taxCode")
    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
        this.taxCodePresent = true;
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

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown enterprise field: " + field);
    }
}
