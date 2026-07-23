package com.agricore.farm;

import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmIntegrationTest {

    private static final String WORKER_SUBJECT = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Test
    void createFarmAndPlot_flow() throws Exception {
        String code = "FARM-" + System.nanoTime();

        MvcResult farmResult = mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "manager-1")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"Buon Ma Thuot Farm",
                                  "address":"Dak Lak",
                                  "province":"Dak Lak",
                                  "totalAreaHa":120.5,
                                  "latitude":12.6667,
                                  "longitude":108.0500
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        JsonNode farm = objectMapper.readTree(farmResult.getResponse().getContentAsString());
        String farmId = farm.get("id").asText();
        OutboxEventEntity farmCreated = outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(farmId))
                .findFirst()
                .orElseThrow();
        JsonNode farmCreatedEnvelope = objectMapper.readTree(farmCreated.getPayload());
        assertThat(farmCreatedEnvelope.path("eventId").asText()).isEqualTo(farmCreated.getId().toString());
        assertThat(farmCreatedEnvelope.path("payload").has("enterpriseId")).isFalse();

        mockMvc.perform(post("/api/v1/farms/" + farmId + "/plots")
                        .header("X-Dev-User", "manager-1")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"P-01",
                                  "name":"Robusta Block A",
                                  "areaInHectares":5.25,
                                  "soilType":"BASALT",
                                  "latitude":12.67,
                                  "longitude":108.05
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.code").value("P-01"));

        mockMvc.perform(get("/api/v1/farms/" + farmId)
                        .header("X-Dev-User", WORKER_SUBJECT)
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/farms/" + farmId + "/memberships")
                        .header("X-Dev-User", "manager-1")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s"}
                                """.formatted(WORKER_SUBJECT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value(WORKER_SUBJECT));

        mockMvc.perform(get("/api/v1/farms/" + farmId)
                        .header("X-Dev-User", WORKER_SUBJECT)
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Buon Ma Thuot Farm"));

        mockMvc.perform(get("/api/v1/farms/" + farmId + "/plots")
                        .header("X-Dev-User", WORKER_SUBJECT)
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        // OAuth2 resource server returns 401 when no credentials are presented
        mockMvc.perform(get("/api/v1/farms"))
                .andExpect(status().isUnauthorized());
    }
}
