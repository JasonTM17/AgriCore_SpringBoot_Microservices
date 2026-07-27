package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrrigationZoneValidationIntegrationTest extends IrrigationZoneApiTestSupport {

    @Test
    void rejectsDuplicateCodesAndInvalidCreatePayloads() throws Exception {
        String owner = compactId();
        String plotId = createPlot(owner, createFarm(owner), null).get("id").asText();
        createIrrigationZone(owner, plotId, "ZONE-A", "Zone A", "DRIP", 100.0);

        mockMvc.perform(post("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validIrrigationZoneRequest(
                                        "zone-a",
                                        "Duplicate",
                                        "SPRINKLER",
                                        110.0
                                )
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IRRIGATION_ZONE_CODE_EXISTS"));

        ObjectNode invalid = validIrrigationZoneRequest(
                "ß".repeat(64),
                "Invalid",
                "DRIP",
                0.0
        );
        invalid.put("targetMoisturePercent", 101);
        mockMvc.perform(post("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        ObjectNode unknownCreateField = validIrrigationZoneRequest(
                "ZONE-UNKNOWN",
                "Unknown field",
                "DRIP",
                100.0
        );
        unknownCreateField.put("flowRateLitresPerMinute", 100.0);
        mockMvc.perform(post("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(unknownCreateField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void rejectsEmptyNullStaleAndOverflowingUpdates() throws Exception {
        String owner = compactId();
        String plotId = createPlot(owner, createFarm(owner), null).get("id").asText();
        JsonNode zone = createIrrigationZone(owner, plotId, "ZONE-B", "Zone B", "DRIP", 100.0);
        String path = "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}";

        assertPatchError(owner, plotId, zone, """
                {"version":0}
                """, "IRRIGATION_ZONE_EMPTY_UPDATE");
        assertPatchError(owner, plotId, zone, """
                {"version":0,"status":null}
                """, "IRRIGATION_ZONE_FIELD_REQUIRED");
        assertPatchError(owner, plotId, zone, """
                {"version":0,"state":"INACTIVE"}
                """, "MALFORMED_JSON");

        mockMvc.perform(patch(path, plotId, zone.get("id").asText())
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch(path, plotId, zone.get("id").asText())
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"ACTIVE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IRRIGATION_ZONE_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .queryParam("page", "1000001")
                        .queryParam("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void assertPatchError(
            String owner,
            String plotId,
            JsonNode zone,
            String content,
            String code
    ) throws Exception {
        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotId,
                                zone.get("id").asText()
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }
}
