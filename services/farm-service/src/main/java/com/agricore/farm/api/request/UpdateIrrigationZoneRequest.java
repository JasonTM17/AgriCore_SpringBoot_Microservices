package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UpdateIrrigationZoneRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @Size(min = 1, max = 200)
    private String name;
    private boolean namePresent;

    @Pattern(regexp = "(?i)DRIP|SPRINKLER|MICRO_SPRINKLER|CENTER_PIVOT|FLOOD|MANUAL")
    private String method;
    private boolean methodPresent;

    @DecimalMin("0.01")
    @DecimalMax("999999.99")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal flowRateLitersPerMinute;
    private boolean flowRatePresent;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal targetMoisturePercent;
    private boolean targetMoisturePresent;

    @Pattern(regexp = "(?i)ACTIVE|MAINTENANCE|INACTIVE")
    private String status;
    private boolean statusPresent;

    @Size(max = 1000)
    private String notes;
    private boolean notesPresent;

    public Long version() { return version; }
    public String name() { return name; }
    public boolean namePresent() { return namePresent; }
    public String method() { return method; }
    public boolean methodPresent() { return methodPresent; }
    public BigDecimal flowRateLitersPerMinute() { return flowRateLitersPerMinute; }
    public boolean flowRatePresent() { return flowRatePresent; }
    public BigDecimal targetMoisturePercent() { return targetMoisturePercent; }
    public boolean targetMoisturePresent() { return targetMoisturePresent; }
    public String status() { return status; }
    public boolean statusPresent() { return statusPresent; }
    public String notes() { return notes; }
    public boolean notesPresent() { return notesPresent; }

    public void setVersion(Long version) { this.version = version; }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    @JsonSetter("method")
    public void setMethod(String method) {
        this.method = method;
        this.methodPresent = true;
    }

    @JsonSetter("flowRateLitersPerMinute")
    public void setFlowRateLitersPerMinute(BigDecimal flowRate) {
        this.flowRateLitersPerMinute = flowRate;
        this.flowRatePresent = true;
    }

    @JsonSetter("targetMoisturePercent")
    public void setTargetMoisturePercent(BigDecimal targetMoisture) {
        this.targetMoisturePercent = targetMoisture;
        this.targetMoisturePresent = true;
    }

    @JsonSetter("status")
    public void setStatus(String status) {
        this.status = status;
        this.statusPresent = true;
    }

    @JsonSetter("notes")
    public void setNotes(String notes) {
        this.notes = notes;
        this.notesPresent = true;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown irrigation-zone field: " + field);
    }
}
