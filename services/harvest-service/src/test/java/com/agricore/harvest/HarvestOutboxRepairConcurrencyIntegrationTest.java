package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.api.response.HarvestCompletionEventStatusResponse;
import com.agricore.harvest.application.service.HarvestCompletionEventRepairService;
import com.agricore.harvest.infrastructure.messaging.OutboxPublisher;
import com.agricore.harvest.infrastructure.messaging.OutboxRetryProperties;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class HarvestOutboxRepairConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private HarvestCompletionEventRepairService repairService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void repair_returnsPendingWithoutWaitingForInFlightPublish() throws Exception {
        outboxRepository.deleteAll();
        harvestRepository.deleteAll();
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID plotId = UUID.fromString(harvest.get("plotId").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity original = outboxRepository.findById(eventId).orElseThrow();

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch repairPassedAccessCheck = new CountDownLatch(1);
        CompletableFuture<SendResult<String, String>> brokerResult = new CompletableFuture<>();
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate(sendStarted, brokerResult);
        OutboxPublisher publisher = new OutboxPublisher(
                outboxRepository,
                kafkaTemplate,
                transactionManager,
                5_000,
                new OutboxRetryProperties(100, 100, 10)
        );
        clearInvocations(farmAccessClient);
        doAnswer(invocation -> {
            repairPassedAccessCheck.countDown();
            return new com.agricore.farmaccess.FarmResourceAccess(
                    HarvestTestAccessSupport.FARM_ID,
                    plotId
            );
        }).when(farmAccessClient).requirePlot(plotId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> publish = executor.submit(publisher::publishPending);
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<HarvestCompletionEventStatusResponse> repair = executor.submit(
                    () -> repairService.republish(harvestId)
            );
            assertThat(repairPassedAccessCheck.await(5, TimeUnit.SECONDS)).isTrue();
            HarvestCompletionEventStatusResponse response = repair.get(1, TimeUnit.SECONDS);
            assertThat(response.eventId()).isEqualTo(eventId);
            assertThat(response.state()).isEqualTo(HarvestCompletionEventStatusResponse.State.ENQUEUED);

            brokerResult.complete(mock(SendResult.class));
            publish.get(5, TimeUnit.SECONDS);

            OutboxEventEntity published = outboxRepository.findById(eventId).orElseThrow();
            assertThat(published.getPayload()).isEqualTo(original.getPayload());
            assertThat(published.getPublishedAt()).isNotNull();
            assertThat(published.getPublishAttempts()).isEqualTo(1);
            verify(kafkaTemplate).send(original.getTopic(), eventId.toString(), original.getPayload());
        } finally {
            brokerResult.complete(mock(SendResult.class));
            executor.shutdownNow();
        }
    }

    @Test
    void repair_returnsStructuredBusyWhenPublishedRowIsLocked() throws Exception {
        outboxRepository.deleteAll();
        harvestRepository.deleteAll();
        JsonNode harvest = completeHarvest();
        UUID harvestId = UUID.fromString(harvest.get("id").asText());
        UUID eventId = UUID.fromString(harvest.get("lastOutboxEventId").asText());
        OutboxEventEntity published = outboxRepository.findById(eventId).orElseThrow();
        published.markPublished();
        outboxRepository.saveAndFlush(published);

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> locker = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> {
                    outboxRepository.findByIdForUpdate(eventId).orElseThrow();
                    lockHeld.countDown();
                    await(releaseLock);
                }));

        try {
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();

            mockMvc.perform(post("/api/v1/harvests/" + harvestId + "/completion-event/republish")
                            .header("X-Dev-User", "manager")
                            .header("X-Dev-Roles", "FARM_MANAGER"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_BUSY"));

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(outboxRepository.findById(eventId).orElseThrow().getPublishedAt()).isNotNull();
        } finally {
            releaseLock.countDown();
            locker.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch releaseLock) {
        try {
            if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release row lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding row lock", ex);
        }
    }

    private static KafkaTemplate<String, String> kafkaTemplate(
            CountDownLatch sendStarted,
            CompletableFuture<SendResult<String, String>> brokerResult
    ) {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            sendStarted.countDown();
            return brokerResult;
        });
        return kafkaTemplate;
    }

    private JsonNode completeHarvest() throws Exception {
        HarvestTestAccessSupport.authorizeAllPlots(farmAccessClient);
        String response = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REPAIR-RACE-%s","cropCycleId":"%s","plotId":"%s","warehouseId":"%s",
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
}
