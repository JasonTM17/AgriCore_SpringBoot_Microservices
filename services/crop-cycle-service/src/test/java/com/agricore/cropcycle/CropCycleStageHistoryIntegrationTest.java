package com.agricore.cropcycle;

import com.agricore.cropcycle.infrastructure.persistence.CropCycleStageHistoryJpaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCycleStageHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CropCycleStageHistoryJpaRepository historyRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void stageHistory_isOrderedAuditableAndIdempotent() throws Exception {
        UUID cycleId = createCycle();

        mockMvc.perform(get("/api/v1/crop-cycles/{cycleId}/stage-history", cycleId)
                        .header("X-Dev-User", "auditor-a")
                        .header("X-Dev-Roles", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cropCycleId").value(cycleId.toString()))
                .andExpect(jsonPath("$.content[0].previousStage").doesNotExist())
                .andExpect(jsonPath("$.content[0].stage").value("PLANNED"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].notes").value("Initial agronomy plan"))
                .andExpect(jsonPath("$.content[0].changedBy").value("agronomist-a"))
                .andExpect(jsonPath("$.content[0].cycleVersion").value(0));

        changeStage(cycleId, "LAND_PREPARATION");
        changeStage(cycleId, "LAND_PREPARATION");

        assertThat(historyRepository.countByCropCycleId(cycleId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/crop-cycles/{cycleId}/stage-history", cycleId)
                        .header("X-Dev-User", "auditor-a")
                        .header("X-Dev-Roles", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].previousStage").value("PLANNED"))
                .andExpect(jsonPath("$.content[0].stage").value("LAND_PREPARATION"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].notes").value("Beds prepared"))
                .andExpect(jsonPath("$.content[0].changedBy").value("manager-a"))
                .andExpect(jsonPath("$.content[0].cycleVersion").value(1))
                .andExpect(jsonPath("$.content[1].stage").value("PLANNED"));
    }

    private UUID createCycle() throws Exception {
        String response = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist-a")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"HISTORY-%d",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2027-03-01",
                                  "plannedEndDate":"2027-11-30",
                                  "notes":"Initial agronomy plan"
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

    private void changeStage(UUID cycleId, String stage) throws Exception {
        mockMvc.perform(patch("/api/v1/crop-cycles/{cycleId}/stage", cycleId)
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"%s","notes":"Beds prepared"}
                                """.formatted(stage)))
                .andExpect(status().isOk());
    }
}
