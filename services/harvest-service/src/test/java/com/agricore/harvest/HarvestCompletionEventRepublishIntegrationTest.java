package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestCompletionEventRepublishIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @BeforeEach
    void authorizePlots() {
        HarvestTestAccessSupport.authorizeAllPlots(farmAccessClient);
    }

    @Test
    void republish_requeuesTheSamePublishedEventAndRepeatedRequestsAreIdempotent() throws Exception {
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity event = outboxRepository.findById(eventId).orElseThrow();
        String originalPayload = event.getPayload();
        Instant originalCreatedAt = event.getCreatedAt();
        event.markPublished();
        outboxRepository.saveAndFlush(event);
        long harvestCount = harvestRepository.count();
        long outboxCount = outboxRepository.count();

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(post(republishPath(harvestId))
                            .header("X-Dev-User", "manager")
                            .header("X-Dev-Roles", "FARM_MANAGER"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.harvestId").value(harvestId.toString()))
                    .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                    .andExpect(jsonPath("$.producer").value("HARVEST"))
                    .andExpect(jsonPath("$.state").value("ENQUEUED"))
                    .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                    .andExpect(jsonPath("$.publishAttempts").value(0));
        }

        OutboxEventEntity requeued = outboxRepository.findById(eventId).orElseThrow();
        assertThat(requeued.getId()).isEqualTo(eventId);
        assertThat(requeued.getPayload()).isEqualTo(originalPayload);
        assertThat(requeued.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(requeued.getPublishedAt()).isNull();
        assertThat(requeued.getPublishAttempts()).isZero();
        assertThat(requeued.getLastError()).isNull();
        assertThat(requeued.getNextAttemptAt()).isNull();
        assertThat(requeued.getQuarantinedAt()).isNull();
        assertThat(harvestRepository.count()).isEqualTo(harvestCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
        assertThat(harvestRepository.findById(harvestId).orElseThrow().getLastOutboxEventId())
                .isEqualTo(eventId);
    }

    @Test
    void republish_releasesQuarantinedEventAndClearsDeliveryState() throws Exception {
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity event = outboxRepository.findById(eventId).orElseThrow();
        event.markFailed("invalid topic", Instant.now(), 60_000, 1);
        outboxRepository.saveAndFlush(event);

        mockMvc.perform(post(republishPath(harvestId))
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("ENQUEUED"))
                .andExpect(jsonPath("$.publishAttempts").value(0));

        assertClearedDeliveryState(outboxRepository.findById(eventId).orElseThrow());
    }

    @Test
    void republish_releasesDeferredEventAndClearsDeliveryState() throws Exception {
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity event = outboxRepository.findById(eventId).orElseThrow();
        event.markFailed("broker unavailable", Instant.now(), 60_000, Integer.MAX_VALUE);
        outboxRepository.saveAndFlush(event);

        mockMvc.perform(post(republishPath(harvestId))
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("ENQUEUED"))
                .andExpect(jsonPath("$.publishAttempts").value(0));

        assertClearedDeliveryState(outboxRepository.findById(eventId).orElseThrow());
    }

    @Test
    void republish_rejectsLegacyHarvestWithoutStableEventIdentity() throws Exception {
        HarvestBatchEntity legacy = persistLegacyHarvest();

        mockMvc.perform(post(republishPath(legacy.getId()))
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_UNAVAILABLE"));
    }

    @Test
    void republish_requiresACompletionRoleBeforeResourceLookup() throws Exception {
        mockMvc.perform(post(republishPath(UUID.randomUUID()))
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(farmAccessClient);
    }

    @Test
    void republish_requiresAuthentication() throws Exception {
        mockMvc.perform(post(republishPath(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode completeHarvest() throws Exception {
        String response = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REPAIR-%s","cropCycleId":"%s","plotId":"%s","warehouseId":"%s",
                                 "productCode":"COFFEE","grossWeightKg":100,"netWeightKg":90,"qualityGrade":"GRADE_A"}
                                """.formatted(
                                System.nanoTime(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private HarvestBatchEntity persistLegacyHarvest() {
        Instant now = Instant.now();
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode("REPAIR-LEGACY-" + System.nanoTime());
        batch.setCropCycleId(UUID.randomUUID());
        batch.setPlotId(UUID.randomUUID());
        batch.setWarehouseId(UUID.randomUUID());
        batch.setProductCode("COFFEE");
        batch.setGrossWeightKg(new BigDecimal("100.000"));
        batch.setNetWeightKg(new BigDecimal("90.000"));
        batch.setQualityGrade("GRADE_A");
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setHarvestedAt(now);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return harvestRepository.saveAndFlush(batch);
    }

    private static void assertClearedDeliveryState(OutboxEventEntity event) {
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getPublishAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getNextAttemptAt()).isNull();
        assertThat(event.getQuarantinedAt()).isNull();
    }

    private static String republishPath(UUID harvestId) {
        return "/api/v1/harvests/" + harvestId + "/completion-event/republish";
    }
}
