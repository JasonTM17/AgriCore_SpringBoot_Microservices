package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmEnterpriseAccessBoundaryIntegrationTest extends FarmEnterpriseApiTestSupport {

    @Test
    void farmManagerCannotAssignOrClearEnterprise() throws Exception {
        String enterpriseId = createEnterprise("LOCKED-ENT", "Locked Enterprise", "LOCKED-TAX")
                .get("id").asText();
        String manager = UUID.randomUUID().toString();
        JsonNode farm = createFarm(manager, "FARM_MANAGER", "Manager Farm", null);
        String farmId = farm.get("id").asText();

        ObjectNode createRequest = objectMapper.createObjectNode();
        createRequest.put("code", "F-" + compactId());
        createRequest.put("name", "Forbidden Linked Farm");
        createRequest.put("enterpriseId", enterpriseId);
        mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(manager, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ENTERPRISE_ADMIN_REQUIRED"));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(manager, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"enterpriseId":"%s"}
                                """.formatted(enterpriseId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ENTERPRISE_ADMIN_REQUIRED"));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(manager, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"enterpriseId":null}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ENTERPRISE_ADMIN_REQUIRED"));
    }

    @Test
    void enterpriseFilterDoesNotBypassFarmMembership() throws Exception {
        String enterpriseId = createEnterprise("SAFE-ENT", "Safe Enterprise", "SAFE-TAX")
                .get("id").asText();
        createLinkedFarm(enterpriseId);
        String unrelatedManager = UUID.randomUUID().toString();
        createFarm(unrelatedManager, "FARM_MANAGER", "Accessible Farm", null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/farms")
                        .headers(devAuth(unrelatedManager, "FARM_MANAGER"))
                        .queryParam("enterpriseId", enterpriseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
