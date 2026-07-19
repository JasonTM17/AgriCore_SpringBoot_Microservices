package com.agricore.cropcycle;

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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void legalProgression_toCompleted() throws Exception {
        String code = "CC-" + System.nanoTime();
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-11-30",
                                  "notes":"Robusta season"
                                }
                                """.formatted(code, farmId, plotId, cropId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("PLANNED"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String cycleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Illegal jump PLANNED -> COMPLETED must fail
        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"COMPLETED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_TRANSITION"));

        advance(cycleId, "LAND_PREPARATION");
        advance(cycleId, "SOWING");
        advance(cycleId, "GROWING");
        advance(cycleId, "HARVESTING");
        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/crop-cycles/" + cycleId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void overlappingActiveCycles_onSamePlot_rejected() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"CC-A-%d",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-01-01",
                                  "plannedEndDate":"2026-06-30"
                                }
                                """.formatted(System.nanoTime(), farmId, plotId, cropId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"CC-B-%d",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-09-30"
                                }
                                """.formatted(System.nanoTime(), farmId, plotId, cropId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CROP_CYCLE_OVERLAP"));
    }

    private void advance(String cycleId, String stage) throws Exception {
        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"%s"}
                                """.formatted(stage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value(stage));
    }
}
