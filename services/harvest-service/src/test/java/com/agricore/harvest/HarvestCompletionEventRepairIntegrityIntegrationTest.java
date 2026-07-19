package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestCompletionEventRepairIntegrityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void republish_rejectsMissingOutboxWithoutRecreatingIt() throws Exception {
        HarvestBatchEntity harvest = persistHarvest(UUID.randomUUID());
        long harvestCount = harvestRepository.count();
        long outboxCount = outboxRepository.count();

        mockMvc.perform(republishRequest(harvest.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_NOT_FOUND"));

        assertThat(harvestRepository.count()).isEqualTo(harvestCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
    }

    @Test
    void republish_rejectsCorruptDirectPayloadWithoutRequeueingTheRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        HarvestBatchEntity harvest = persistHarvest(eventId);
        insertPublishedEvent(
                eventId,
                harvest.getId(),
                "{\"eventId\":\"" + UUID.randomUUID() + "\"}"
        );

        mockMvc.perform(republishRequest(harvest.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_ID_MISMATCH"));

        OutboxEventEntity unchanged = outboxRepository.findById(eventId).orElseThrow();
        assertThat(unchanged.getPublishedAt()).isNotNull();
        assertThat(unchanged.getPublishAttempts()).isEqualTo(1);
    }

    @Test
    void republish_requeuesTransitionalRowUsingStableEnvelopeIdentity() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID transitionalRowId = UUID.randomUUID();
        HarvestBatchEntity harvest = persistHarvest(eventId);
        long outboxCount = outboxRepository.count();
        String payload = HarvestCompletionEventTestEnvelope.valid(eventId, harvest)
                .replace(eventId.toString(), eventId.toString().toUpperCase());
        insertPublishedEvent(transitionalRowId, harvest.getId(), payload);

        mockMvc.perform(republishRequest(harvest.getId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.state").value("RETRYING"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.publishAttempts").value(1));

        OutboxEventEntity requeued = outboxRepository.findById(transitionalRowId).orElseThrow();
        assertThat(requeued.getId()).isEqualTo(transitionalRowId);
        assertThat(requeued.getPayload()).isEqualTo(payload);
        assertThat(requeued.getPublishedAt()).isNull();
        assertThat(outboxRepository.count()).isEqualTo(outboxCount + 1);
    }

    private void insertPublishedEvent(UUID rowId, UUID harvestId, String payload) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at,
                     published_at, publish_attempts)
                VALUES (?, 'HarvestBatch', ?, 'HarvestCompleted.v1', 'agricore.harvest.events',
                        ?, ?, ?, 1)
                """,
                rowId,
                harvestId.toString(),
                payload,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private HarvestBatchEntity persistHarvest(UUID eventId) {
        Instant now = Instant.now();
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode("REPAIR-INTEGRITY-" + System.nanoTime());
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

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder republishRequest(
            UUID harvestId
    ) {
        return post("/api/v1/harvests/" + harvestId + "/completion-event/republish")
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
