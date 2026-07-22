package com.agricore.harvest;

import com.agricore.common.event.EventTypes;
import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.api.request.CompleteHarvestBatchRequest;
import com.agricore.harvest.api.request.StartHarvestRequest;
import com.agricore.harvest.application.service.HarvestLifecycleService;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private HarvestLifecycleService lifecycleService;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void startAndCompletePublishEachLifecycleFactExactlyOnce() throws Exception {
        MvcResult started = startHarvest();
        JsonNode startedBody = objectMapper.readTree(started.getResponse().getContentAsString());
        String harvestId = startedBody.path("id").asText();
        String startedAt = startedBody.path("startedAt").asText();

        assertEventTypes(harvestId, EventTypes.HARVEST_BATCH_CREATED, EventTypes.HARVEST_STARTED);
        for (OutboxEventEntity event : eventsFor(harvestId)) {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(envelope.path("payload").path("status").asText()).isEqualTo("IN_PROGRESS");
            assertThat(envelope.path("payload").path("startedAt").asText()).isEqualTo(startedAt);
        }

        String completionBody = """
                {
                  "grossWeightKg":3500.000,
                  "netWeightKg":3300.000,
                  "qualityGrade":"grade_a",
                  "notes":"Quality checked",
                  "farmName":"Dak Lak Farm",
                  "plotCode":"DL-A01",
                  "productName":"Robusta Coffee",
                  "careSummary":"Drip irrigation"
                }
                """;
        MvcResult completed = mockMvc.perform(authenticated(post("/api/v1/harvests/{harvestId}/complete", harvestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.startedAt").value(startedAt))
                .andExpect(jsonPath("$.harvestedAt").isNotEmpty())
                .andExpect(jsonPath("$.qualityGrade").value("GRADE_A"))
                .andExpect(jsonPath("$.lastOutboxEventId").isNotEmpty())
                .andReturn();
        String completionEventId = objectMapper.readTree(completed.getResponse().getContentAsString())
                .path("lastOutboxEventId").asText();

        mockMvc.perform(authenticated(post("/api/v1/harvests/{harvestId}/complete", harvestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastOutboxEventId").value(completionEventId));

        assertEventTypes(
                harvestId,
                EventTypes.HARVEST_BATCH_CREATED,
                EventTypes.HARVEST_STARTED,
                EventTypes.HARVEST_COMPLETED
        );
        OutboxEventEntity completedEvent = eventsFor(harvestId).stream()
                .filter(event -> EventTypes.HARVEST_COMPLETED.equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(completedEvent.getId().toString()).isEqualTo(completionEventId);
    }

    @Test
    void invalidCompletionKeepsHarvestInProgressAndWritesNoCompletionEvent() throws Exception {
        JsonNode started = objectMapper.readTree(startHarvest().getResponse().getContentAsString());
        String harvestId = started.path("id").asText();

        mockMvc.perform(authenticated(post("/api/v1/harvests/{harvestId}/complete", harvestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grossWeightKg":100.000,
                                  "netWeightKg":101.000,
                                  "qualityGrade":"GRADE_A"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEIGHT"));

        mockMvc.perform(authenticated(post("/api/v1/harvests/{harvestId}/complete", harvestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grossWeightKg":100.000,
                                  "netWeightKg":95.000,
                                  "qualityGrade":"GRADE_A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(eventsFor(harvestId).stream()
                .filter(event -> EventTypes.HARVEST_COMPLETED.equals(event.getEventType())))
                .hasSize(1);
    }

    @Test
    void concurrentCompletionWritesOneCompletionEvent() throws Exception {
        UUID plotId = UUID.randomUUID();
        var started = lifecycleService.start(new StartHarvestRequest(
                "CONCURRENT-" + UUID.randomUUID(),
                UUID.randomUUID(),
                plotId,
                UUID.randomUUID(),
                "COFFEE-ROBUSTA",
                null
        ));
        CountDownLatch bothAuthorized = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothAuthorized.countDown();
            assertThat(bothAuthorized.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(farmAccessClient).requirePlot(plotId);
        CompleteHarvestBatchRequest request = new CompleteHarvestBatchRequest(
                new BigDecimal("3500.000"),
                new BigDecimal("3300.000"),
                "GRADE_A",
                null,
                null,
                null,
                null,
                null
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> lifecycleService.complete(started.id(), request));
            var second = executor.submit(() -> lifecycleService.complete(started.id(), request));
            assertThat(first.get(10, TimeUnit.SECONDS).lastOutboxEventId())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).lastOutboxEventId());
        }

        assertThat(eventsFor(started.id().toString()).stream()
                .filter(event -> EventTypes.HARVEST_COMPLETED.equals(event.getEventType())))
                .hasSize(1);
    }

    private MvcResult startHarvest() throws Exception {
        return mockMvc.perform(authenticated(post("/api/v1/harvests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"HL-%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "warehouseId":"%s",
                                  "productCode":"coffee-robusta",
                                  "notes":"Morning harvest"
                                }
                                """.formatted(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.grossWeightKg").value(nullValue()))
                .andExpect(jsonPath("$.netWeightKg").value(nullValue()))
                .andExpect(jsonPath("$.qualityGrade").value(nullValue()))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.harvestedAt").value(nullValue()))
                .andExpect(jsonPath("$.lastOutboxEventId").value(nullValue()))
                .andReturn();
    }

    private List<OutboxEventEntity> eventsFor(String harvestId) {
        return outboxRepository.findAll().stream()
                .filter(event -> harvestId.equals(event.getAggregateId()))
                .toList();
    }

    private void assertEventTypes(String harvestId, String... eventTypes) {
        assertThat(eventsFor(harvestId))
                .extracting(OutboxEventEntity::getEventType)
                .containsExactlyInAnyOrder(eventTypes);
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "mgr")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
