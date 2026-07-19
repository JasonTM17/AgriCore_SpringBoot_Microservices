package com.agricore.iot;

import com.agricore.farmaccess.FarmAccessClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IotErrorBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void malformedJson_returnsStableBadRequest() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")),
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON"
        );
    }

    @Test
    void unsupportedMediaType_returnsStableApiError() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @Test
    void invalidRequest_returnsValidationDetails() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED"
        ).andExpect(jsonPath("$.violations.length()").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000000", "1.00001"})
    void readingOutsideNumericStorageBoundary_isRejected(String metricValue) throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"PROBE","metricType":"SOIL_MOISTURE",
                                 "metricValue":%s,"unit":"PCT"}
                                """.formatted(metricValue))),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED"
        );
    }

    private static ResultActions assertApiError(
            ResultActions result,
            HttpStatus expectedStatus,
            String expectedCode
    ) throws Exception {
        return result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.getReasonPhrase()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").isString());
    }
}
