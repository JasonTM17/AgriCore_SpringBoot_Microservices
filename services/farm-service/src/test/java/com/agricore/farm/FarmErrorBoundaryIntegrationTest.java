package com.agricore.farm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmErrorBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedJson_returnsStableBadRequest() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/farms"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/farms"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void invalidPathIdentifier_returnsStableBadRequest() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/farms/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Invalid request parameter: farmId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "?page=-1",
            "?size=0",
            "?size=101",
            "?sort=unknown,desc",
            "?sort=createdAt,sideways",
            "?status=DELETED"
    })
    void invalidListParameters_returnValidationError(String query) throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/farms" + query)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void caseInsensitiveStatusAndSortDirection_remainSupported() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/farms")
                        .queryParam("status", "active")
                        .queryParam("sort", "createdAt,DESC")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidFarmStatus_isRejectedBeforeResourceLookup() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/farms/{farmId}", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELETED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidPlotStatus_isRejectedBeforeResourceLookup() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/plots/{plotId}", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELETED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "manager-1")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
