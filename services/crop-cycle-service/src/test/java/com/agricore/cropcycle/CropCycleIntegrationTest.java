package com.agricore.cropcycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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

    @Test
    void createAndAdvanceStage_toCompleted() throws Exception {
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

        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"SOWING","notes":"Planted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SOWING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

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
}
