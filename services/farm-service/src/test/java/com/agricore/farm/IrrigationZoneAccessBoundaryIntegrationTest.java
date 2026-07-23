package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrrigationZoneAccessBoundaryIntegrationTest extends IrrigationZoneApiTestSupport {

    @Test
    void hidesForeignPlotsAndRejectsZoneSubstitution() throws Exception {
        String ownerA = compactId();
        String ownerB = compactId();
        String farmA = createFarm(ownerA);
        String farmB = createFarm(ownerB);
        String plotA = createPlot(ownerA, farmA, null).get("id").asText();
        String plotB = createPlot(ownerB, farmB, null).get("id").asText();
        JsonNode zoneB = createIrrigationZone(
                ownerB,
                plotB,
                "FOREIGN",
                "Foreign zone",
                "DRIP",
                120.0
        );

        mockMvc.perform(get("/api/v1/plots/{plotId}/irrigation-zones", plotB)
                        .headers(devAuth(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotA,
                                zoneB.get("id").asText()
                        )
                        .headers(devAuth(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IRRIGATION_ZONE_NOT_FOUND"));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotB,
                                zoneB.get("id").asText()
                        )
                        .headers(devAuth(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"INACTIVE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotB,
                                zoneB.get("id").asText()
                        )
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmB));
    }
}
