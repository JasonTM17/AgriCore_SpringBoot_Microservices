package com.agricore.cropcatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCareProfileManagementIntegrationTest {

    private static final String TOMATO_ID = "22222222-2222-2222-2222-222222222006";
    private static final String COFFEE_ID = "22222222-2222-2222-2222-222222222001";
    private static final String LETTUCE_ID = "22222222-2222-2222-2222-222222222005";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void agronomistReplacesProfileAtomicallyWithAuditAndVersion() throws Exception {
        String firstResponse = mockMvc.perform(put("/api/v1/admin/crops/{cropId}/care-profile", TOMATO_ID)
                        .header("X-Dev-User", "agronomist@example.com")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.growthRequirement.version").value(1))
                .andExpect(jsonPath("$.growthRequirement.updatedBy").value("agronomist@example.com"))
                .andExpect(jsonPath("$.commonDiseases.length()").value(1))
                .andExpect(jsonPath("$.commonDiseases[0].code").value("TOMATO_BACTERIAL_SPOT"))
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.recommendations[0].sortOrder").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String diseaseId = objectMapper.readTree(firstResponse).at("/commonDiseases/0/id").asText();
        String recommendationId = objectMapper.readTree(firstResponse).at("/recommendations/0/id").asText();
        String secondRequest = validRequest(1).replace(
                "Measure root-zone moisture before irrigation.",
                "Measure root-zone moisture before every irrigation."
        );

        mockMvc.perform(put("/api/v1/admin/crops/{cropId}/care-profile", TOMATO_ID)
                        .header("X-Dev-User", "agronomist@example.com")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.growthRequirement.version").value(2))
                .andExpect(jsonPath("$.commonDiseases[0].id").value(diseaseId))
                .andExpect(jsonPath("$.recommendations[0].id").value(recommendationId))
                .andExpect(jsonPath("$.recommendations[0].description")
                        .value("Measure root-zone moisture before every irrigation."));
    }

    @Test
    void staleVersionReturnsActionableConflictWithoutMutation() throws Exception {
        mockMvc.perform(put("/api/v1/admin/crops/{cropId}/care-profile", COFFEE_ID)
                        .header("X-Dev-User", "agronomist@example.com")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARE_PROFILE_VERSION_CONFLICT"));
    }

    @Test
    void fieldWorkerCannotManageGlobalCatalog() throws Exception {
        mockMvc.perform(put("/api/v1/admin/crops/{cropId}/care-profile", COFFEE_ID)
                        .header("X-Dev-User", "field-worker@example.com")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void invalidIntervalsAreRejectedBeforePersistence() throws Exception {
        String request = validRequest(0).replace(
                "\"irrigationIntervalDaysMin\": 2",
                "\"irrigationIntervalDaysMin\": 5"
        );
        mockMvc.perform(put("/api/v1/admin/crops/{cropId}/care-profile", LETTUCE_ID)
                        .header("X-Dev-User", "agronomist@example.com")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROWTH_INTERVAL"));
    }

    private static String validRequest(long version) {
        return """
                {
                  "version": %d,
                  "growthRequirement": {
                    "irrigationIntervalDaysMin": 2,
                    "irrigationIntervalDaysMax": 4,
                    "fertilizationIntervalDaysMin": 12,
                    "fertilizationIntervalDaysMax": 18,
                    "waterRequirementMmPerWeek": 31.50,
                    "notes": "Adjust after checking soil moisture."
                  },
                  "commonDiseases": [{
                    "code": "TOMATO_BACTERIAL_SPOT",
                    "name": "Bacterial spot",
                    "symptoms": "Small dark leaf and fruit lesions.",
                    "prevention": "Use clean seedlings and sanitize tools.",
                    "treatment": "Remove affected tissue and follow local guidance."
                  }],
                  "recommendations": [{
                    "category": "IRRIGATION",
                    "title": "Verify soil moisture",
                    "description": "Measure root-zone moisture before irrigation.",
                    "growthStage": "GROWING",
                    "sortOrder": 5
                  }]
                }
                """.formatted(version);
    }
}
