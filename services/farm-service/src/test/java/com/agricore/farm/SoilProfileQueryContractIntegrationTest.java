package com.agricore.farm;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
class SoilProfileQueryContractIntegrationTest extends SoilProfileApiTestSupport {

    @Test
    void treatsLikeMetacharactersAsLiteralQueryText() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        createSoilProfile(owner, plotId, "SOIL-A", LocalDate.of(2026, 6, 1), 6.25);
        createSoilProfile(owner, plotId, "SOIL-B", LocalDate.of(2026, 6, 2), 6.50);

        expectQueryCount(owner, plotId, "soil-", 2);
        expectQueryCount(owner, plotId, "%", 0);
        expectQueryCount(owner, plotId, "_", 0);
        expectQueryCount(owner, plotId, "!", 0);
    }

    @Test
    void rejectsUnsafePageOffsetsAndExpandingUnicodeCodes() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();

        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .queryParam("page", "1000001")
                        .queryParam("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        ObjectNode invalidCode = validSoilProfileRequest(
                "ß".repeat(64),
                LocalDate.of(2026, 6, 1),
                6.25
        );
        mockMvc.perform(post("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsNullStatusEvenWhenNotesArePresent() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        String profileId = createSoilProfile(
                owner,
                plotId,
                "PATCH-NULL",
                LocalDate.of(2026, 6, 3),
                6.75
        ).get("id").asText();

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                profileId
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":null,"notes":"must not bypass status validation"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_STATUS_REQUIRED"));
    }

    private void expectQueryCount(String owner, String plotId, String query, int count)
            throws Exception {
        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .queryParam("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count));
    }
}
