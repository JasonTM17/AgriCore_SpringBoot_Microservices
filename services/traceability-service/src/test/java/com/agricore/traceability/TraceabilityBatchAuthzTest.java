package com.agricore.traceability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves POST /api/v1/traceability/batches is role-gated on the shipped controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityBatchAuthzTest {

    @Autowired
    private MockMvc mockMvc;

    private static String body() {
        return """
                {
                  "eventId":"%s",
                  "harvestBatchId":"%s",
                  "cropCycleId":"%s",
                  "plotId":"%s",
                  "farmName":"Authz Farm",
                  "plotCode":"AZ-1",
                  "productName":"Robusta",
                  "harvestDate":"2026-03-15",
                  "qualityGrade":"GRADE_A",
                  "netWeightKg":100
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "TRACEABILITY_READ"})
    void matchingWarehouseRoleWithoutUsePermissionCannotReadAcknowledgement(String explicitPermissions)
            throws Exception {
        mockMvc.perform(get("/api/v1/traceability/events/harvest-completed/{eventId}/acknowledgement",
                        UUID.randomUUID())
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .header("X-Dev-Permissions", explicitPermissions))
                .andExpect(status().isForbidden());
    }

    @Test
    void fieldWorker_cannotWriteBatch() throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseManager_canWriteBatch() throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated());
    }
}
