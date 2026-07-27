package com.agricore.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void stampsATimestampAndLeavesTheOptionalFieldsUnset() {
        ApiError error = ApiError.of(
                404,
                "Not Found",
                "CROP_NOT_FOUND",
                "Crop not found",
                "/api/v1/crops/1",
                null
        );

        assertThat(error.timestamp()).isNotNull();
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.code()).isEqualTo("CROP_NOT_FOUND");
        assertThat(error.violations()).isNull();
        assertThat(error.details()).isNull();
    }

    @Test
    void validationFactoryFixesStatusAndCode() {
        ApiError error = ApiError.validation(
                "Request validation failed",
                "/api/v1/notifications",
                null,
                List.of(new ApiError.FieldViolation("channel", "must not be blank", ""))
        );

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.error()).isEqualTo("Bad Request");
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.violations()).singleElement()
                .extracting(ApiError.FieldViolation::field)
                .isEqualTo("channel");
    }

    @Test
    void omitsUnsetFieldsFromTheSerializedBody() throws Exception {
        String json = objectMapper.writeValueAsString(
                ApiError.of(
                        409,
                        "Conflict",
                        "DUPLICATE_RESOURCE",
                        "already exists",
                        "/api/v1/farms",
                        null
                )
        );

        assertThat(json)
                .doesNotContain("violations")
                .doesNotContain("details")
                .doesNotContain("traceId")
                .contains("\"code\":\"DUPLICATE_RESOURCE\"")
                .contains("\"status\":409");
    }

    @Test
    void keepsViolationsWhenPresent() throws Exception {
        String json = objectMapper.writeValueAsString(ApiError.validation(
                "Request validation failed",
                "/api/v1/notifications",
                "trace-1",
                List.of(new ApiError.FieldViolation("recipient", "must not be blank", null))
        ));

        assertThat(json)
                .contains("\"violations\"")
                .contains("\"field\":\"recipient\"")
                .contains("\"traceId\":\"trace-1\"");
    }

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

    @Test
    void keepsAViolationWhoseRejectedValueWasNull() throws Exception {
        String json = objectMapper.writeValueAsString(
                new ApiError.FieldViolation("subject", "must not be blank", null)
        );

        assertThat(json).contains("\"field\":\"subject\"");
    }
}
