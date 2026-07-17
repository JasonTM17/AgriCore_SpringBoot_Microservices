package com.agricore.cropcycle;

import com.agricore.common.event.EventTypes;
import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterizes the crop-cycle transactional outbox contract:
 * create emits CropCycleCreated; legal stage change emits StageChanged with previousStage;
 * illegal transition writes no outbox row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCycleOutboxContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Test
    void create_writesCropCycleCreatedEnvelope() throws Exception {
        long before = outboxRepository.count();
        String code = "OBX-" + System.nanoTime();
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-11-30"
                                }
                                """.formatted(code, farmId, plotId, cropId)))
                .andExpect(status().isCreated())
                .andReturn();

        String cycleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        assertThat(outboxRepository.count()).isEqualTo(before + 1);

        OutboxEventEntity event = findLatestForCycle(cycleId);
        assertThat(event.getEventType()).isEqualTo(EventTypes.CROP_CYCLE_CREATED);
        assertThat(event.getTopic()).isEqualTo("agricore.crop-cycle.events");

        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.CROP_CYCLE_CREATED);
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("crop-cycle-service");
        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("cropCycleId").asText()).isEqualTo(cycleId);
        assertThat(payload.get("code").asText()).isEqualTo(code);
        assertThat(payload.get("farmId").asText()).isEqualTo(farmId.toString());
        assertThat(payload.get("plotId").asText()).isEqualTo(plotId.toString());
        assertThat(payload.get("cropId").asText()).isEqualTo(cropId.toString());
        assertThat(payload.get("stage").asText()).isEqualTo("PLANNED");
        assertThat(payload.get("status").asText()).isEqualTo("DRAFT");
        assertThat(payload.has("previousStage")).isFalse();
    }

    @Test
    void legalStageChange_writesStageChangedWithPreviousStage() throws Exception {
        String code = "OBX2-" + System.nanoTime();
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-11-30"
                                }
                                """.formatted(code, farmId, plotId, cropId)))
                .andExpect(status().isCreated())
                .andReturn();
        String cycleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        long afterCreate = outboxRepository.count();

        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"LAND_PREPARATION","notes":"prep"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LAND_PREPARATION"));

        assertThat(outboxRepository.count()).isEqualTo(afterCreate + 1);
        OutboxEventEntity event = findLatestForCycle(cycleId);
        assertThat(event.getEventType()).isEqualTo(EventTypes.CROP_CYCLE_STAGE_CHANGED);
        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.CROP_CYCLE_STAGE_CHANGED);
        assertThat(envelope.get("payload").get("previousStage").asText()).isEqualTo("PLANNED");
        assertThat(envelope.get("payload").get("stage").asText()).isEqualTo("LAND_PREPARATION");
        assertThat(envelope.get("payload").get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void illegalStageTransition_writesNoOutboxEvent() throws Exception {
        String code = "OBX3-" + System.nanoTime();
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "farmId":"%s",
                                  "plotId":"%s",
                                  "cropId":"%s",
                                  "plannedStartDate":"2026-03-01",
                                  "plannedEndDate":"2026-11-30"
                                }
                                """.formatted(code, farmId, plotId, cropId)))
                .andExpect(status().isCreated())
                .andReturn();
        String cycleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        long afterCreate = outboxRepository.count();

        mockMvc.perform(post("/api/v1/crop-cycles/" + cycleId + "/stage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"COMPLETED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_TRANSITION"));

        assertThat(outboxRepository.count()).isEqualTo(afterCreate);
    }

    private OutboxEventEntity findLatestForCycle(String cycleId) {
        List<OutboxEventEntity> all = outboxRepository.findAll();
        return all.stream()
                .filter(e -> e.getAggregateId() != null && e.getAggregateId().equals(cycleId)
                        || (e.getPayload() != null && e.getPayload().contains(cycleId)))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no outbox row for cycle " + cycleId));
    }
}
