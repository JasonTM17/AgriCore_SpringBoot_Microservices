package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrrigationZoneManagementIntegrationTest extends IrrigationZoneApiTestSupport {

    @Test
    void createsReadsAndUpdatesOperationalConfiguration() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        JsonNode created = createIrrigationZone(
                owner,
                plotId,
                "north-drip",
                "North drip line",
                "DRIP",
                125.50
        );

        String zoneId = created.get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals("NORTH-DRIP", created.get("code").asText());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", created.get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(owner, created.get("createdBy").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, created.get("version").asLong());

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotId,
                                zoneId
                        )
                        .headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId))
                .andExpect(jsonPath("$.plotId").value(plotId));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/irrigation-zones/{zoneId}",
                                plotId,
                                zoneId
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "name": "North sprinkler",
                                  "method": "SPRINKLER",
                                  "flowRateLitersPerMinute": 220.75,
                                  "targetMoisturePercent": 42.50,
                                  "status": "MAINTENANCE",
                                  "notes": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North sprinkler"))
                .andExpect(jsonPath("$.method").value("SPRINKLER"))
                .andExpect(jsonPath("$.flowRateLitersPerMinute").value(220.75))
                .andExpect(jsonPath("$.targetMoisturePercent").value(42.50))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").value(owner))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void filtersSortsAndTreatsQueryMetacharactersLiterally() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        createIrrigationZone(owner, plotId, "DRIP-A", "Alpha zone", "DRIP", 90.0);
        createIrrigationZone(owner, plotId, "SPRAY-B", "Beta zone", "SPRINKLER", 180.0);

        mockMvc.perform(get("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .queryParam("method", "sprinkler")
                        .queryParam("q", "beta")
                        .queryParam("sort", "flowRateLitersPerMinute,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("SPRAY-B"));

        for (String query : new String[]{"%", "_", "!"}) {
            mockMvc.perform(get("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                            .headers(devAuth(owner))
                            .queryParam("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    void paginatesEqualSortValuesWithStableDistinctRows() throws Exception {
        String owner = compactId();
        String plotId = createPlot(owner, createFarm(owner), null).get("id").asText();
        createIrrigationZone(owner, plotId, "TIE-A", "Same name", "DRIP", 90.0);
        createIrrigationZone(owner, plotId, "TIE-B", "Same name", "DRIP", 90.0);

        Set<String> ids = new HashSet<>();
        for (int page = 0; page < 2; page++) {
            String body = mockMvc.perform(get("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                            .headers(devAuth(owner))
                            .queryParam("page", Integer.toString(page))
                            .queryParam("size", "1")
                            .queryParam("sort", "name,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            ids.add(objectMapper.readTree(body).get("content").get(0).get("id").asText());
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, ids.size());
    }
}
