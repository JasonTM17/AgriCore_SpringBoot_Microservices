package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoilProfileAccessBoundaryIntegrationTest extends SoilProfileApiTestSupport {

    @Test
    void hidesForeignPlotsAndRejectsProfileSubstitution() throws Exception {
        String ownerA = compactId();
        String ownerB = compactId();
        String farmA = createFarm(ownerA);
        String farmB = createFarm(ownerB);
        String plotA = createPlot(ownerA, farmA, null).get("id").asText();
        String plotB = createPlot(ownerB, farmB, null).get("id").asText();
        JsonNode profileB = createSoilProfile(
                ownerB,
                plotB,
                "FOREIGN",
                LocalDate.of(2026, 4, 20),
                6.75
        );

        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotB)
                        .headers(devAuth(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/plots/{plotId}/soil-profiles", plotB)
                        .headers(devAuth(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validSoilProfileRequest("FORBIDDEN", LocalDate.of(2026, 4, 21), 6.5)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotA,
                                profileB.get("id").asText()
                        )
                        .headers(devAuth(ownerA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_NOT_FOUND"));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotB,
                                profileB.get("id").asText()
                        )
                        .headers(devAuth(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"ARCHIVED"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLOT_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotB,
                                profileB.get("id").asText()
                        )
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmB));
    }
}
