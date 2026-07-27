package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
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
class FarmAreaManagementIntegrationTest extends FarmAreaApiTestSupport {

    @Test
    void createsFiltersUpdatesAndDeletesUnusedAreas() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        JsonNode north = createArea(owner, farmId, "north", "North Production", 12.5);
        JsonNode south = createArea(owner, farmId, "south", "South Production", 5.0);

        mockMvc.perform(patch("/api/v1/farms/{farmId}/areas/{areaId}", farmId, south.get("id").asText())
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"South Maintenance","status":"MAINTENANCE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SOUTH"))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.updatedBy").value(owner))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .queryParam("status", "MAINTENANCE")
                        .queryParam("q", "south")
                        .queryParam("sort", "areaInHectares,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(south.get("id").asText()));

        mockMvc.perform(delete("/api/v1/farms/{farmId}/areas/{areaId}", farmId, north.get("id").asText())
                        .headers(devAuth(owner))
                        .queryParam("version", "0"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas/{areaId}", farmId, north.get("id").asText())
                        .headers(devAuth(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_AREA_NOT_FOUND"));
    }

    @Test
    void rejectsDuplicateCodesAndStaleUpdatesWithoutMutation() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);
        JsonNode area = createArea(owner, farmId, "BLOCK-A", "Block A", 3.0);

        mockMvc.perform(post("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"block-a","name":"Duplicate","areaInHectares":2.0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_AREA_CODE_EXISTS"));

        mockMvc.perform(patch("/api/v1/farms/{farmId}/areas/{areaId}", farmId, area.get("id").asText())
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":99,"name":"Stale Name"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_AREA_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas/{areaId}", farmId, area.get("id").asText())
                        .headers(devAuth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Block A"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void rejectsUnsupportedSortBeforeRepositoryAccess() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);

        mockMvc.perform(get("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .queryParam("sort", "farmId,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void validatesNumericPrecision() throws Exception {
        String owner = compactId();
        String farmId = createFarm(owner);

        mockMvc.perform(post("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PRECISE","name":"Precise Area","areaInHectares":1.2345}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaInHectares").value(1.2345));

        mockMvc.perform(post("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TOO-PRECISE","name":"Invalid Area","areaInHectares":1.23456}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TOO-LARGE","name":"Invalid Area","areaInHectares":10000000000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    }
}
