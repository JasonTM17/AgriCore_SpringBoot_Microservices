package com.agricore.farm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmEnterpriseValidationIntegrationTest extends FarmEnterpriseApiTestSupport {

    @Test
    void rejectsInvalidPatchShapesAndUnknownEnterprise() throws Exception {
        String farmId = createFarm(
                "system-admin",
                "SYSTEM_ADMIN",
                "Validation Farm",
                null
        ).get("id").asText();

        assertBadPatch(farmId, "{\"version\":0}", "FARM_EMPTY_UPDATE");
        assertBadPatch(farmId, "{\"version\":0,\"name\":\"   \"}", "FARM_FIELD_REQUIRED");
        assertBadPatch(farmId, "{\"version\":0,\"status\":null}", "FARM_FIELD_REQUIRED");
        assertBadPatch(farmId, "{\"version\":0,\"mystery\":true}", "MALFORMED_JSON");

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"enterpriseId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTERPRISE_NOT_FOUND"));
    }

    @Test
    void rejectsStaleAssignmentAndUnsafePagingWithoutMutation() throws Exception {
        String enterpriseId = createEnterprise("STALE-ENT", "Stale Enterprise", "STALE-TAX")
                .get("id").asText();
        String farmId = createLinkedFarm(enterpriseId).get("id").asText();

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Version One"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"enterpriseId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enterpriseId").value(enterpriseId));

        mockMvc.perform(get("/api/v1/farms")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .queryParam("page", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void treatsProvinceWildcardsAsLiteralText() throws Exception {
        createFarm("system-admin", "SYSTEM_ADMIN", "Literal Search Farm", null);

        for (String query : new String[]{"%", "_", "!"}) {
            mockMvc.perform(get("/api/v1/farms")
                            .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                            .queryParam("province", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    private void assertBadPatch(String farmId, String body, String code) throws Exception {
        mockMvc.perform(patch("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }
}
