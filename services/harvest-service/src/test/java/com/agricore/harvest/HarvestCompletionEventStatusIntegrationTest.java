package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
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
class HarvestCompletionEventStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void status_reportsEnqueuedAndPublishedStateUsingStableEventIdentity() throws Exception {
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity outboxEvent = outboxRepository.findAll().stream()
                .filter(event -> event.getPayload().contains("\"harvestId\":\"" + harvestId + "\""))
                .findFirst()
                .orElseThrow();

        assertThat(outboxEvent.getId()).isEqualTo(eventId);

        mockMvc.perform(statusRequest(harvestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvestId").value(harvestId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.producer").value("HARVEST"))
                .andExpect(jsonPath("$.state").value("ENQUEUED"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.publishAttempts").value(0));

        outboxEvent.markFailed("broker unavailable");
        outboxRepository.saveAndFlush(outboxEvent);

        mockMvc.perform(statusRequest(harvestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRYING"))
                .andExpect(jsonPath("$.publishAttempts").value(1));

        outboxEvent.markPublished();
        outboxRepository.saveAndFlush(outboxEvent);

        mockMvc.perform(statusRequest(harvestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(notNullValue()))
                .andExpect(jsonPath("$.publishAttempts").value(2));
    }

    @Test
    void status_marksLegacyHarvestWithoutEventIdentityUnavailable() throws Exception {
        HarvestBatchEntity legacy = persistHarvest(null);

        mockMvc.perform(statusRequest(legacy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvestId").value(legacy.getId().toString()))
                .andExpect(jsonPath("$.eventId").value(nullValue()))
                .andExpect(jsonPath("$.producer").value("HARVEST"))
                .andExpect(jsonPath("$.state").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.createdAt").value(nullValue()))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.publishAttempts").value(0));
    }

    @Test
    void status_readsTransitionalOutboxRowsWhoseRowIdPredatesStableEventIdentity() throws Exception {
        UUID eventId = UUID.randomUUID();
        HarvestBatchEntity harvest = persistHarvest(eventId);
        UUID transitionalRowId = UUID.randomUUID();
        String payload = HarvestCompletionEventTestEnvelope.valid(eventId, harvest)
                .replace(eventId.toString(), eventId.toString().toUpperCase());
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at, publish_attempts)
                VALUES (?, 'HarvestBatch', ?, 'HarvestCompleted.v1', 'agricore.harvest.events', ?, ?, 0)
                """,
                transitionalRowId,
                harvest.getId().toString(),
                payload,
                java.sql.Timestamp.from(Instant.now())
        );

        mockMvc.perform(statusRequest(harvest.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.state").value("ENQUEUED"));
    }

    @Test
    void status_requiresAuthentication() throws Exception {
        mockMvc.perform(get(statusPath(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode completeHarvest() throws Exception {
        String response = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"STATUS-%s","cropCycleId":"%s","plotId":"%s","warehouseId":"%s",
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

    private HarvestBatchEntity persistHarvest(UUID eventId) {
        Instant now = Instant.now();
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode("STATUS-BOUNDARY-" + System.nanoTime());
        batch.setCropCycleId(UUID.randomUUID());
        batch.setPlotId(UUID.randomUUID());
        batch.setWarehouseId(UUID.randomUUID());
        batch.setProductCode("COFFEE");
        batch.setGrossWeightKg(new BigDecimal("100.000"));
        batch.setNetWeightKg(new BigDecimal("90.000"));
        batch.setQualityGrade("GRADE_A");
        batch.setStatus(HarvestStatus.COMPLETED);
        batch.setHarvestedAt(now);
        batch.setLastOutboxEventId(eventId);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return harvestRepository.saveAndFlush(batch);
    }

    private static MockHttpServletRequestBuilder statusRequest(UUID harvestId) {
        return get(statusPath(harvestId))
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }

    private static String statusPath(UUID harvestId) {
        return "/api/v1/harvests/" + harvestId + "/completion-event";
    }
}
