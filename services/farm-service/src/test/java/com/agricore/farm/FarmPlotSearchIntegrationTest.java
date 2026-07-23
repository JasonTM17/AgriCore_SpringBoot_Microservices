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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmPlotSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void filtersAndSortsPlotsWithinAuthorizedFarm() throws Exception {
        String owner = UUID.randomUUID().toString();
        String farmId = createFarm(owner);
        String northArea = UUID.randomUUID().toString();
        String southArea = UUID.randomUUID().toString();
        createPlot(owner, farmId, "P-A", "North Small", northArea, 1.25);
        createPlot(owner, farmId, "P-B", "North Large", northArea, 3.50);
        createPlot(owner, farmId, "P-C", "South Block", southArea, 2.00);

        mockMvc.perform(get("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(owner))
                        .queryParam("status", "AVAILABLE")
                        .queryParam("areaId", northArea)
                        .queryParam("q", "north")
                        .queryParam("sort", "areaInHectares,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].code").value("P-B"))
                .andExpect(jsonPath("$.content[1].code").value("P-A"));
    }

    @Test
    void rejectsUnsupportedSortBeforeRepositoryAccess() throws Exception {
        String owner = UUID.randomUUID().toString();
        String farmId = createFarm(owner);

        mockMvc.perform(get("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(owner))
                        .queryParam("sort", "farmId,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void systemAdminReceivesNotFoundForUnknownFarm() throws Exception {
        mockMvc.perform(get("/api/v1/farms/{farmId}/plots", UUID.randomUUID())
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
    }

    private String createFarm(String owner) throws Exception {
        String body = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"F-%s","name":"Search Farm"}
                                """.formatted(UUID.randomUUID().toString().replace("-", ""))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void createPlot(
            String owner,
            String farmId,
            String code,
            String name,
            String areaId,
            double hectares
    ) throws Exception {
        mockMvc.perform(post("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"%s",
                                  "areaId":"%s",
                                  "areaInHectares":%s
                                }
                                """.formatted(code, name, areaId, hectares)))
                .andExpect(status().isCreated());
    }

    private static HttpHeaders devAuth(String owner) {
        return devAuth(owner, "FARM_MANAGER");
    }

    private static HttpHeaders devAuth(String owner, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-User", owner);
        headers.set("X-Dev-Roles", role);
        return headers;
    }
}
