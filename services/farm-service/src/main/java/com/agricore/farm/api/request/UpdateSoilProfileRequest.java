package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpdateSoilProfileRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @Pattern(regexp = "(?i)ACTIVE|ARCHIVED")
    private String status;

    private boolean statusPresent;

    @Size(max = 1000)
    private String notes;

    private boolean notesPresent;

    public UpdateSoilProfileRequest() {
    }

    public Long version() { return version; }
    public String status() { return status; }
    public boolean statusPresent() { return statusPresent; }
    public String notes() { return notes; }
    public boolean notesPresent() { return notesPresent; }

    public void setVersion(Long version) { this.version = version; }
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
}
