package com.agricore.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validation_doesNotSerializeRejectedInputValues() throws Exception {
        ApiError error = ApiError.validation(
                "Request validation failed",
                "/api/v1/auth/login",
                "trace-1",
                List.of(new ApiError.FieldViolation("password", "must not be blank", "Secret123!"))
        );

        String json = objectMapper.writeValueAsString(error);

        assertThat(json)
                .doesNotContain("Secret123!")
                .doesNotContain("rejectedValue");
    }
}
