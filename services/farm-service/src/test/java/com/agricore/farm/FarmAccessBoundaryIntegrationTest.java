package com.agricore.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmAccessBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void farmAndPlotBoundaries_rejectCrossFarmSubstitution() throws Exception {
        String ownerA = subject();
        String ownerB = subject();
        String farmA = createFarm(ownerA, "Farm A");
        String farmB = createFarm(ownerB, "Farm B");
        String plotA = createPlot(ownerA, farmA, "P-A");
        String plotB = createPlot(ownerB, farmB, "P-B");

        mockMvc.perform(get("/api/v1/farms").headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(farmA));

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmB).headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));
        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmB)
                        .headers(devAuth(ownerA, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Forbidden update\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/farms/{farmId}/plots", farmB)
                        .headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/plots/{plotId}", plotB)
                        .headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/plots/{plotId}", plotB)
                        .headers(devAuth(ownerA, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Forbidden update\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get("/internal/api/v1/farm-access/plots/{plotId}", plotA)
                        .headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmA))
                .andExpect(jsonPath("$.plotId").value(plotA));
        mockMvc.perform(get("/internal/api/v1/farm-access/plots/{plotId}", plotB)
                        .headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));
        mockMvc.perform(get("/internal/api/v1/farm-access/farms/{farmId}/plots/{plotId}", farmA, plotB)
                        .headers(devAuth(ownerA, "FARM_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));
        mockMvc.perform(get("/internal/api/v1/farm-access/farms/{farmId}/plots/{plotId}", farmA, plotB)
                        .headers(devAuth(ownerB, "FARM_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmB)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/api/v1/farm-access/plots/{plotId}", plotB)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmB));
    }

    private String createFarm(String owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"F-%s","name":"%s"}
                                """.formatted(UUID.randomUUID().toString().replace("-", ""), name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createPlot(String owner, String farmId, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Plot","areaInHectares":1.25}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static HttpHeaders devAuth(String subject, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-User", subject);
        headers.set("X-Dev-Roles", role);
        return headers;
    }

    private static String subject() {
        return UUID.randomUUID().toString();
    }
}
