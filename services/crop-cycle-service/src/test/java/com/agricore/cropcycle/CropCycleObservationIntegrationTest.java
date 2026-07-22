package com.agricore.cropcycle;

import com.agricore.cropcycle.infrastructure.persistence.CropCycleObservationJpaRepository;
import com.agricore.farmaccess.FarmAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCycleObservationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CropCycleObservationJpaRepository observationRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void observations_areTrimmedFarmScopedAndOrderedByObservedTime() throws Exception {
        UUID cycleId = createCycle();

        createObservation(
                cycleId, "GROWTH", "INFO", "2026-07-10T08:00:00Z",
                "First leaves", "  Four healthy leaves  "
        );
        createObservation(
                cycleId, "PEST", "ATTENTION", "2026-07-20T08:00:00Z",
                "Pest pressure", "Aphids on two plants"
        );

        assertThat(observationRepository.countByCropCycleId(cycleId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/crop-cycles/{cycleId}/observations", cycleId)
                        .header("X-Dev-User", "auditor-a")
                        .header("X-Dev-Roles", "AUDITOR")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].cropCycleId").value(cycleId.toString()))
                .andExpect(jsonPath("$.content[0].category").value("PEST"))
                .andExpect(jsonPath("$.content[0].severity").value("ATTENTION"))
                .andExpect(jsonPath("$.content[0].title").value("Pest pressure"))
                .andExpect(jsonPath("$.content[0].details").value("Aphids on two plants"))
                .andExpect(jsonPath("$.content[0].observedAt").value("2026-07-20T08:00:00Z"))
                .andExpect(jsonPath("$.content[0].recordedBy").value("field-worker-a"));
    }

    @Test
    void createObservation_rejectsInvalidInputAndUnauthorizedRoleWithoutWriting() throws Exception {
        UUID cycleId = createCycle();
        clearInvocations(farmAccessClient);

        mockMvc.perform(post("/api/v1/crop-cycles/{cycleId}/observations", cycleId)
                        .header("X-Dev-User", "sales-a")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationJson(
                                "GROWTH", "INFO", "2026-07-20T08:00:00Z", "Healthy canopy", "No issue"
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(farmAccessClient);

        mockMvc.perform(post("/api/v1/crop-cycles/{cycleId}/observations", cycleId)
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationJson(
                                "GROWTH", "INFO", "2999-01-01T00:00:00Z", "Future reading", "Invalid time"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("observedAt"));

        assertThat(observationRepository.countByCropCycleId(cycleId)).isZero();
    }

    private UUID createCycle() throws Exception {
        String response = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist-a")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"OBS-%d",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-11-30"
                                }
                                """.formatted(
                                System.nanoTime(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void createObservation(
            UUID cycleId,
            String category,
            String severity,
            String observedAt,
            String title,
            String details
    ) throws Exception {
        mockMvc.perform(post("/api/v1/crop-cycles/{cycleId}/observations", cycleId)
                        .header("X-Dev-User", "  field-worker-a  ")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationJson(category, severity, observedAt, title, details)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title.trim()))
                .andExpect(jsonPath("$.details").value(details.trim()))
                .andExpect(jsonPath("$.recordedBy").value("field-worker-a"));
    }

    private static String observationJson(
            String category,
            String severity,
            String observedAt,
            String title,
            String details
    ) {
        return """
                {
                  "category":"%s",
                  "severity":"%s",
                  "title":"%s",
                  "details":"%s",
                  "observedAt":"%s"
                }
                """.formatted(category, severity, title, details, observedAt);
    }
}
