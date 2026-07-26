package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestCompletionEventRepairCorruptionIntegrationTest {

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

    @BeforeEach
    void authorizePlots() {
        HarvestTestAccessSupport.authorizeAllPlots(farmAccessClient);
    }

    @Test
    void republish_rejectsWrongTopicWithoutRequeueing() throws Exception {
        Fixture fixture = fixture();
        insertPublishedEvent(fixture, "agricore.corrupt.events", fixture.envelope());

        assertInvalidAndUnchanged(fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventType", "eventVersion", "occurredAt", "producer"})
    void republish_rejectsInvalidEnvelopeMetadataWithoutRequeueing(String field) throws Exception {
        Fixture fixture = fixture();
        ObjectNode envelope = (ObjectNode) objectMapper.readTree(fixture.envelope());
        switch (field) {
            case "eventType" -> envelope.put(field, "OtherEvent.v1");
            case "eventVersion" -> envelope.put(field, 2);
            case "occurredAt" -> envelope.put(field, "not-an-instant");
            case "producer" -> envelope.put(field, "other-service");
            default -> throw new IllegalArgumentException(field);
        }
        insertPublishedEvent(fixture, "agricore.harvest.events", objectMapper.writeValueAsString(envelope));

        assertInvalidAndUnchanged(fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"harvestId", "harvestBatchId", "cropCycleId", "plotId", "warehouseId"})
    void republish_rejectsMismatchedPayloadResourceWithoutRequeueing(String field) throws Exception {
        Fixture fixture = fixture();
        ObjectNode envelope = (ObjectNode) objectMapper.readTree(fixture.envelope());
        ((ObjectNode) envelope.get("payload")).put(field, UUID.randomUUID().toString());
        insertPublishedEvent(fixture, "agricore.harvest.events", objectMapper.writeValueAsString(envelope));

        assertInvalidAndUnchanged(fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"productCode", "grossWeightKg", "netWeightKg", "qualityGrade", "harvestDate"})
    void republish_rejectsPayloadValueThatDiffersFromHarvest(String field) throws Exception {
        Fixture fixture = fixture();
        ObjectNode envelope = (ObjectNode) objectMapper.readTree(fixture.envelope());
        ObjectNode payload = (ObjectNode) envelope.get("payload");
        switch (field) {
            case "productCode" -> payload.put(field, "RICE");
            case "grossWeightKg", "netWeightKg" -> payload.put(field, 1);
            case "qualityGrade" -> payload.put(field, "GRADE_B");
            case "harvestDate" -> payload.put(field, "2000-01-01");
            default -> throw new IllegalArgumentException(field);
        }
        insertPublishedEvent(fixture, "agricore.harvest.events", objectMapper.writeValueAsString(envelope));

        assertInvalidAndUnchanged(fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"warehouseId", "productCode", "netWeightKg", "qualityGrade"})
    void republish_rejectsMissingConsumerFieldWithoutRequeueing(String field) throws Exception {
        Fixture fixture = fixture();
        ObjectNode envelope = (ObjectNode) objectMapper.readTree(fixture.envelope());
        ((ObjectNode) envelope.get("payload")).remove(field);
        insertPublishedEvent(fixture, "agricore.harvest.events", objectMapper.writeValueAsString(envelope));

        assertInvalidAndUnchanged(fixture);
    }

    private void assertInvalidAndUnchanged(Fixture fixture) throws Exception {
        mockMvc.perform(get("/api/v1/harvests/" + fixture.harvest().getId() + "/completion-event")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_INVALID"));

        mockMvc.perform(post("/api/v1/harvests/" + fixture.harvest().getId() + "/completion-event/republish")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_INVALID"));

        OutboxEventEntity unchanged = outboxRepository.findById(fixture.eventId()).orElseThrow();
        assertThat(unchanged.getPublishedAt()).isNotNull();
        assertThat(unchanged.getPublishAttempts()).isEqualTo(1);
    }

    private Fixture fixture() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        HarvestBatchEntity harvest = new HarvestBatchEntity();
        harvest.setId(UUID.randomUUID());
        harvest.setCode("REPAIR-CORRUPT-" + System.nanoTime());
        harvest.setCropCycleId(UUID.randomUUID());
        harvest.setPlotId(UUID.randomUUID());
        harvest.setWarehouseId(UUID.randomUUID());
        harvest.setProductCode("COFFEE");
        harvest.setGrossWeightKg(new BigDecimal("100.000"));
        harvest.setNetWeightKg(new BigDecimal("90.000"));
        harvest.setQualityGrade("GRADE_A");
        harvest.setStatus(HarvestStatus.COMPLETED);
        harvest.setHarvestedAt(now);
        harvest.setLastOutboxEventId(eventId);
        harvest.setCreatedAt(now);
        harvest.setUpdatedAt(now);
        HarvestBatchEntity saved = harvestRepository.saveAndFlush(harvest);
        return new Fixture(eventId, saved, HarvestCompletionEventTestEnvelope.valid(eventId, saved));
    }

    private void insertPublishedEvent(Fixture fixture, String topic, String payload) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at,
                     published_at, publish_attempts)
                VALUES (?, 'HarvestBatch', ?, 'HarvestCompleted.v1', ?, ?, ?, ?, 1)
                """,
                fixture.eventId(),
                fixture.harvest().getId().toString(),
                topic,
                payload,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private record Fixture(UUID eventId, HarvestBatchEntity harvest, String envelope) {
    }
}
