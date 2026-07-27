package com.agricore.farm.api.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnterpriseRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{0,63}") String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 250) String legalName,
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9.-]{0,63}") String taxCode,
        @Size(max = 500) String address,
        @Size(max = 120) String province
) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown enterprise field: " + field);
    }
}
