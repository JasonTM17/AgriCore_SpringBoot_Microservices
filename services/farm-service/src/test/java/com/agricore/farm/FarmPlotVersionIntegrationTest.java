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
class FarmPlotVersionIntegrationTest extends FarmAreaApiTestSupport {

    @Test
    void rejectsStaleFarmAndPlotUpdatesWithoutMutation() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        JsonNode plot = createPlot(owner, farmId, null);
        String plotId = plot.get("id").asText();

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Versioned Farm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Versioned Farm"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Stale Farm"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId).headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Versioned Farm"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/plots/{plotId}", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Versioned Plot"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Versioned Plot"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/plots/{plotId}", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Stale Plot"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLOT_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/plots/{plotId}", plotId).headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Versioned Plot"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void requiresNonNegativeClientVersions() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        String plotId = createPlot(owner, farmId, null).get("id").asText();

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Missing farm version"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/v1/plots/{plotId}", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":-1,"name":"Invalid plot version"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
