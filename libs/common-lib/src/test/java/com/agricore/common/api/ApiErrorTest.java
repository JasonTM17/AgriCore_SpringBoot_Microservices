package com.agricore.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error envelope every service returns. Its field names and its omission rules are part of the
 * public contract — a client branches on {@code code} and reads {@code violations} only when the
 * failure was a validation failure.
 */
class ApiErrorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void stampsATimestampAndLeavesTheOptionalFieldsUnset() {
        ApiError error = ApiError.of(404, "Not Found", "CROP_NOT_FOUND", "Crop not found", "/api/v1/crops/1", null);

        assertThat(error.timestamp()).isNotNull();
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.code()).isEqualTo("CROP_NOT_FOUND");
        assertThat(error.violations()).isNull();
        assertThat(error.details()).isNull();
    }

    /**
     * The validation factory fixes status, reason, and code rather than taking them, so a caller
     * cannot emit a validation failure under some other code.
     */
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

    /**
     * {@code @JsonInclude(NON_NULL)} is what keeps a plain 404 from shipping {@code "violations":
     * null}. Clients test for the key's presence, so dropping the annotation would be a contract
     * change that no compiler catches.
     */
    @Test
    void omitsUnsetFieldsFromTheSerializedBody() throws Exception {
        String json = objectMapper.writeValueAsString(
                ApiError.of(409, "Conflict", "DUPLICATE_RESOURCE", "already exists", "/api/v1/farms", null));

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

    /**
     * A rejected value of {@code null} is meaningful — it says the field was absent rather than
     * malformed — so the violation entry must survive serialization with the field name intact.
     */
    @Test
    void keepsAViolationWhoseRejectedValueWasNull() throws Exception {
        String json = objectMapper.writeValueAsString(
                new ApiError.FieldViolation("subject", "must not be blank", null));

        assertThat(json).contains("\"field\":\"subject\"");
    }
}
