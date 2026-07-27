package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmAreaPlotAssignmentIntegrationTest extends FarmAreaApiTestSupport {

    @Test
    void preventsCrossFarmAssignmentAndSupportsPresenceAwareReassignment() throws Exception {
        String ownerA = compactId();
        String ownerB = compactId();
        String farmA = createFarm(ownerA);
        String farmB = createFarm(ownerB);
        JsonNode areaA = createArea(ownerA, farmA, "A-1", "Area A", 4.0);
        JsonNode areaA2 = createArea(ownerA, farmA, "A-2", "Area A2", 3.0);
        JsonNode areaB = createArea(ownerB, farmB, "B-1", "Area B", 5.0);

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas/{areaId}", farmB, areaB.get("id").asText())
                        .headers(devAuth(ownerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas/{areaId}", farmA, areaB.get("id").asText())
                        .headers(devAuth(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_AREA_NOT_FOUND"));

        expectPlotCreateRejected(ownerA, farmA, areaB.get("id").asText());
        expectPlotCreateRejected("system-admin", farmA, areaB.get("id").asText(), "SYSTEM_ADMIN");

        JsonNode plot = createPlot(ownerA, farmA, areaA.get("id").asText());
        String plotId = plot.get("id").asText();
        mockMvc.perform(delete("/api/v1/farms/{farmId}/areas/{areaId}", farmA, areaA.get("id").asText())
                        .headers(devAuth(ownerA))
                        .queryParam("version", "0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_AREA_IN_USE"));

        patchPlotArea(ownerA, plotId, 0, objectMapper.createObjectNode().put("areaId", areaA2.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(areaA2.get("id").asText()));

        patchPlotArea(ownerA, plotId, 1, objectMapper.createObjectNode().put("name", "Renamed Plot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(areaA2.get("id").asText()));

        patchPlotArea(ownerA, plotId, 2, objectMapper.createObjectNode().put("areaId", areaB.get("id").asText()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_AREA_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/plots/{plotId}", plotId).headers(devAuth(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(areaA2.get("id").asText()));

        patchPlotArea(ownerA, plotId, 2, objectMapper.createObjectNode().putNull("areaId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").isEmpty());
    }

    private void expectPlotCreateRejected(String subject, String farmId, String areaId, String... roles)
            throws Exception {
        mockMvc.perform(post("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(subject, roles))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P-%s","name":"Foreign Area Plot","areaInHectares":1.0,"areaId":"%s"}
                                """.formatted(compactId(), areaId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_AREA_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions patchPlotArea(
            String owner,
            String plotId,
            long version,
            ObjectNode request
    ) throws Exception {
        request.put("version", version);
        return mockMvc.perform(patch("/api/v1/plots/{plotId}", plotId)
                .headers(devAuth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }
}
