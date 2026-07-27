package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmEnterpriseManagementIntegrationTest extends FarmEnterpriseApiTestSupport {

    @Test
    void createsFiltersReassignsAndClearsFarmEnterprise() throws Exception {
        String enterpriseA = createEnterprise("PORT-A", "Portfolio A", "PORT-TAX-A")
                .get("id").asText();
        String enterpriseB = createEnterprise("PORT-B", "Portfolio B", "PORT-TAX-B")
                .get("id").asText();
        JsonNode linkedFarm = createLinkedFarm(enterpriseA);
        String farmId = linkedFarm.get("id").asText();
        createLinkedFarm(enterpriseB);

        mockMvc.perform(get("/api/v1/farms")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .queryParam("enterpriseId", enterpriseA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(farmId))
                .andExpect(jsonPath("$.content[0].enterpriseId").value(enterpriseA));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"enterpriseId":"%s"}
                                """.formatted(enterpriseB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enterpriseId").value(enterpriseB))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1,"enterpriseId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enterpriseId").doesNotExist())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void preservesEnterpriseWhenUnrelatedFarmFieldChanges() throws Exception {
        String enterpriseId = createEnterprise("KEEP-ENT", "Keep Enterprise", "KEEP-TAX")
                .get("id").asText();
        String farmId = createLinkedFarm(enterpriseId).get("id").asText();
        String manager = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s"}
                                """.formatted(manager)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(manager, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Renamed Farm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Farm"))
                .andExpect(jsonPath("$.enterpriseId").value(enterpriseId));
    }

    @Test
    void paginatesEqualFarmNamesDeterministicallyWithinEnterprise() throws Exception {
        String enterpriseId = createEnterprise("PAGE-ENT", "Page Enterprise", "PAGE-TAX")
                .get("id").asText();
        createLinkedFarm(enterpriseId);
        createLinkedFarm(enterpriseId);

        Set<String> farmIds = new HashSet<>();
        for (int page = 0; page < 2; page++) {
            String body = mockMvc.perform(get("/api/v1/farms")
                            .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                            .queryParam("enterpriseId", enterpriseId)
                            .queryParam("page", Integer.toString(page))
                            .queryParam("size", "1")
                            .queryParam("sort", "name,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            farmIds.add(objectMapper.readTree(body).get("content").get(0).get("id").asText());
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, farmIds.size());
    }
}
