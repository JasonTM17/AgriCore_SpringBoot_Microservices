package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
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
class SoilProfileManagementIntegrationTest extends SoilProfileApiTestSupport {

    @Test
    void recordsFiltersAndArchivesHistoricalSoilSamples() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        createSoilProfile(owner, plotId, "SOIL-JAN", LocalDate.of(2026, 1, 10), 5.85);
        JsonNode february = createSoilProfile(
                owner,
                plotId,
                "soil-feb",
                LocalDate.of(2026, 2, 12),
                6.40
        );

        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .queryParam("sampledFrom", "2026-02-01")
                        .queryParam("sampledTo", "2026-02-28")
                        .queryParam("q", "feb")
                        .queryParam("sort", "ph,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(february.get("id").asText()))
                .andExpect(jsonPath("$.content[0].sampleCode").value("SOIL-FEB"));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                february.get("id").asText()
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"ARCHIVED","notes":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.notes").isEmpty())
                .andExpect(jsonPath("$.updatedBy").value(owner))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .queryParam("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(february.get("id").asText()));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                february.get("id").asText()
                        )
                        .headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ph").value(6.40))
                .andExpect(jsonPath("$.texture").value("CLAY_LOAM"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsDuplicateStaleAndEmptyUpdatesWithoutMutation() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        JsonNode profile = createSoilProfile(
                owner,
                plotId,
                "SAMPLE-1",
                LocalDate.of(2026, 3, 15),
                6.10
        );

        mockMvc.perform(post("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validSoilProfileRequest("sample-1", LocalDate.of(2026, 3, 16), 6.20)
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_SAMPLE_CODE_EXISTS"));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                profile.get("id").asText()
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":9,"status":"ARCHIVED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_VERSION_CONFLICT"));

        mockMvc.perform(patch(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                profile.get("id").asText()
                        )
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_EMPTY_UPDATE"));

        mockMvc.perform(get(
                                "/api/v1/plots/{plotId}/soil-profiles/{profileId}",
                                plotId,
                                profile.get("id").asText()
                        )
                        .headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void validatesAgronomicRangesAndDateFilters() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();
        expectInvalid(
                owner,
                plotId,
                validSoilProfileRequest("FUTURE", LocalDate.of(2999, 1, 1), 6.25)
        );
        expectInvalid(
                owner,
                plotId,
                validSoilProfileRequest("INVALID-PH", LocalDate.of(2026, 6, 1), 14.01)
        );
        expectInvalid(
                owner,
                plotId,
                validSoilProfileRequest("PRECISE-PH", LocalDate.of(2026, 6, 1), 6.123)
        );
        ObjectNode invalidPercent = validSoilProfileRequest(
                "INVALID-PERCENT",
                LocalDate.of(2026, 6, 1),
                6.25
        );
        invalidPercent.put("organicMatterPercent", 100.01);
        expectInvalid(owner, plotId, invalidPercent);
        ObjectNode invalidNutrient = validSoilProfileRequest(
                "INVALID-NUTRIENT",
                LocalDate.of(2026, 6, 1),
                6.25
        );
        invalidNutrient.put("nitrogenMgKg", 100000000);
        expectInvalid(owner, plotId, invalidNutrient);

        mockMvc.perform(get("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .queryParam("sampledFrom", "2026-05-01")
                        .queryParam("sampledTo", "2026-04-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOIL_PROFILE_DATE_RANGE_INVALID"));
    }

    private void expectInvalid(String owner, String plotId, JsonNode request) throws Exception {
        mockMvc.perform(post("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
