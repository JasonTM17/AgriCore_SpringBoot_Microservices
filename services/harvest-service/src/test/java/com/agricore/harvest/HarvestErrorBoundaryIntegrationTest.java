package com.agricore.harvest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestErrorBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedJson_returnsStableBadRequest() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/harvests/complete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")),
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON"
        );
    }

    @Test
    void unsupportedMediaType_returnsStableApiError() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/harvests/complete"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @Test
    void invalidPathIdentifier_returnsStableBadRequest() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(get("/api/v1/harvests/not-a-uuid"))),
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "100000000000, 1",
            "1.0001, 1"
    })
    void weightOutsideNumericStorageBoundary_isRejected(String grossWeight, String netWeight) throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/harvests/complete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(grossWeight, netWeight))),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED"
        );
    }

    private static void assertApiError(ResultActions result, HttpStatus expectedStatus, String expectedCode)
            throws Exception {
        result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.getReasonPhrase()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").isString());
    }

    private static String validRequest(String grossWeight, String netWeight) {
        return """
                {
                  "code":"HB-%s",
                  "cropCycleId":"%s",
                  "plotId":"%s",
                  "warehouseId":"%s",
                  "productCode":"COFFEE",
                  "grossWeightKg":%s,
                  "netWeightKg":%s,
                  "qualityGrade":"GRADE_A"
                }
                """.formatted(
                System.nanoTime(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                grossWeight,
                netWeight);
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
