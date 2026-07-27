package com.agricore.traceability;

import com.agricore.traceability.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves public provenance cannot be written through a caller-authenticated HTTP boundary and
 * public HTTP failures keep the platform error contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityBatchAuthzTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TraceabilityBatchJpaRepository batchRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;

    @BeforeEach
    void clearPersistence() {
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        batchRepository.deleteAll();
    }

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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIELD_WORKER", "WAREHOUSE_MANAGER", "SYSTEM_ADMIN"})
    void authenticatedCaller_cannotSubmitRandomOrCrossFarmProvenance(String role) throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "caller")
                        .header("X-Dev-Roles", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(batchRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void malformedBodyCannotReopenTheRemovedProvenanceWriteBoundary() throws Exception {
        assertRemovedWriteBoundary("{not json");
    }

    @Test
    void invalidBodyCannotReopenTheRemovedProvenanceWriteBoundary() throws Exception {
        assertRemovedWriteBoundary("""
                {"eventId":"","productName":""}
                """);
    }

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

    private void assertRemovedWriteBoundary(String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(batchRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}
