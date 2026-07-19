package com.agricore.traceability;

import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.ProcessedEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityHarvestProjectionAcknowledgementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private TraceabilityBatchJpaRepository batchRepository;

    @Test
    void acknowledgement_reportsConsumerLedgerState() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(acknowledgementRequest(eventId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.projection").value("TRACEABILITY"))
                .andExpect(jsonPath("$.state").value("NOT_ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(nullValue()));

        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse-user")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(traceabilityProjectionRequest(
                                eventId.toString().toUpperCase(Locale.ROOT),
                                UUID.randomUUID()
                        )))
                .andExpect(status().isCreated());

        mockMvc.perform(acknowledgementRequest(eventId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(notNullValue()));
    }

    @Test
    void acknowledgement_readsLegacyUppercaseUuidMarkers() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID harvestBatchId = UUID.randomUUID();
        processedEventRepository.saveAndFlush(ProcessedEventEntity.of(
                eventId.toString().toUpperCase(Locale.ROOT),
                TraceabilityApplicationService.HARVEST_CONSUMER
        ));
        long batchesBeforeReplay = batchRepository.count();

        mockMvc.perform(acknowledgementRequest(eventId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(notNullValue()));

        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse-user")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(traceabilityProjectionRequest(
                                eventId.toString(),
                                harvestBatchId
                        )))
                .andExpect(status().isConflict());

        assertThat(batchRepository.count()).isEqualTo(batchesBeforeReplay);
    }

    @Test
    void acknowledgement_enforcesHarvestWorkflowRoles() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(get(acknowledgementPath(eventId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(acknowledgementRequest(eventId, "SALES_STAFF"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                                "/api/v1/traceability/events/harvest-completed/not-a-uuid/acknowledgement"
                        )
                        .header("X-Dev-User", "harvest-user")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder acknowledgementRequest(UUID eventId, String roles) {
        return get(acknowledgementPath(eventId))
                .header("X-Dev-User", "harvest-user")
                .header("X-Dev-Roles", roles);
    }

    private static String acknowledgementPath(UUID eventId) {
        return "/api/v1/traceability/events/harvest-completed/" + eventId + "/acknowledgement";
    }

    private static String traceabilityProjectionRequest(String eventId, UUID harvestBatchId) {
        return """
                {
                  "eventId":"%s",
                  "harvestBatchId":"%s",
                  "cropCycleId":"%s",
                  "plotId":"%s",
                  "farmName":"Canonical Farm",
                  "plotCode":"CANON-1",
                  "productName":"Robusta",
                  "harvestDate":"2026-07-19",
                  "qualityGrade":"GRADE_A",
                  "netWeightKg":100
                }
                """.formatted(eventId, harvestBatchId, UUID.randomUUID(), UUID.randomUUID());
    }
}
