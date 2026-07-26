package com.agricore.traceability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves POST /api/v1/traceability/batches is role-gated on the shipped controller, and that every
 * rejection on this service answers in the platform {@code ApiError} shape.
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

    @Test
    void fieldWorker_cannotWriteBatch() throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * The QR lookup is the one endpoint an end consumer reaches directly, unauthenticated, from a
     * printed label — so its failure modes must answer in the platform contract rather than
     * whatever the container would render.
     */
    @Test
    void unknownPathOnThePublicSurfaceIsNotFound() throws Exception {
        mockMvc.perform(get("/public/api/v1/no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodOnTheQrLookupIsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/public/api/v1/traceability/ANYCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unreadableBodyIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void invalidBodyReportsTheOffendingFields() throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"","productName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray());
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
