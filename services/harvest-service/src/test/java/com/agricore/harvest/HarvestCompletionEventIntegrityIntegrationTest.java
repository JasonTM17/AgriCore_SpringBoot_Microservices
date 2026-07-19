package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestCompletionEventIntegrityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void status_rejectsMissingOutboxRowForCurrentHarvest() throws Exception {
        HarvestBatchEntity harvest = persistHarvest(UUID.randomUUID());

        mockMvc.perform(statusRequest(harvest.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_NOT_FOUND"));
    }

    @Test
    void status_rejectsDirectOutboxRowWhosePayloadHasDifferentEventIdentity() throws Exception {
        UUID eventId = UUID.randomUUID();
        HarvestBatchEntity harvest = persistHarvest(eventId);
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at, publish_attempts)
                VALUES (?, 'HarvestBatch', ?, 'HarvestCompleted.v1', 'agricore.harvest.events', ?, ?, 0)
                """,
                eventId,
                harvest.getId().toString(),
                "{\"eventId\":\"" + UUID.randomUUID() + "\"}",
                Timestamp.from(Instant.now())
        );

        mockMvc.perform(statusRequest(harvest.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_ID_MISMATCH"));
    }

    private HarvestBatchEntity persistHarvest(UUID eventId) {
        Instant now = Instant.now();
        HarvestBatchEntity batch = new HarvestBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCode("STATUS-INTEGRITY-" + System.nanoTime());
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

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statusRequest(
            UUID harvestId
    ) {
        return get("/api/v1/harvests/" + harvestId + "/completion-event")
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
