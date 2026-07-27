package com.agricore.traceability;

import com.agricore.common.event.EventTypes;
import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.agricore.traceability.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TraceabilityEventIntegrationTest {

    @Autowired
    private TraceabilityApplicationService applicationService;
    @Autowired
    private TraceabilityBatchJpaRepository batchRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsTwoEventsOnlyOnceAcrossHarvestReplays() throws Exception {
        UUID harvestBatchId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        CreateTraceabilityRequest firstRequest = request(firstEventId, harvestBatchId);
        PublicTraceabilityResponse created = applicationService.createFromHarvest(firstRequest);
        PublicTraceabilityResponse sameEventReplay = applicationService.createFromHarvest(firstRequest);
        PublicTraceabilityResponse newEventReplay = applicationService.createFromHarvest(request(
                secondEventId,
                harvestBatchId
        ));

        assertThat(sameEventReplay.traceabilityCode()).isEqualTo(created.traceabilityCode());
        assertThat(newEventReplay.traceabilityCode()).isEqualTo(created.traceabilityCode());
        assertThat(processedEventRepository.findCanonicalOrLegacy(
                firstEventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        )).isPresent();
        assertThat(processedEventRepository.findCanonicalOrLegacy(
                secondEventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        )).isPresent();
        assertProjectionEvents(harvestBatchId, created.traceabilityCode());
    }

    @Test
    void concurrentReplaysCreateOneProjectionAndOneEventPair() throws Exception {
        UUID harvestBatchId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<PublicTraceabilityResponse> first = createAfterBarrier(
                    executor,
                    ready,
                    start,
                    request(firstEventId, harvestBatchId)
            );
            CompletableFuture<PublicTraceabilityResponse> second = createAfterBarrier(
                    executor,
                    ready,
                    start,
                    request(secondEventId, harvestBatchId)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).traceabilityCode())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).traceabilityCode());
        }

        assertProjectionEvents(
                harvestBatchId,
                batchRepository.findFirstByHarvestBatchId(harvestBatchId).orElseThrow().getTraceabilityCode()
        );
        assertThat(processedEventRepository.findCanonicalOrLegacy(
                firstEventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        )).isPresent();
        assertThat(processedEventRepository.findCanonicalOrLegacy(
                secondEventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        )).isPresent();
    }

    private CompletableFuture<PublicTraceabilityResponse> createAfterBarrier(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            CreateTraceabilityRequest request
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for projection start");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Projection interrupted", exception);
            }
            return applicationService.createFromHarvest(request);
        }, executor);
    }

    private void assertProjectionEvents(UUID harvestBatchId, String traceabilityCode) throws Exception {
        TraceabilityBatchEntity batch = batchRepository.findFirstByHarvestBatchId(harvestBatchId).orElseThrow();
        List<OutboxEventEntity> events = outboxRepository.findAll().stream()
                .filter(event -> batch.getId().toString().equals(event.getAggregateId()))
                .toList();
        assertThat(events).extracting(OutboxEventEntity::getEventType)
                .containsExactlyInAnyOrder(
                        EventTypes.TRACEABILITY_BATCH_CREATED,
                        EventTypes.TRACEABILITY_CODE_GENERATED
                );
        for (OutboxEventEntity event : events) {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(envelope.path("producer").asText()).isEqualTo("traceability-service");
            assertThat(envelope.path("payload").path("traceabilityCode").asText())
                    .isEqualTo(traceabilityCode);
        }
    }

    private static CreateTraceabilityRequest request(UUID eventId, UUID harvestBatchId) {
        return new CreateTraceabilityRequest(
                eventId,
                harvestBatchId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Dak Lak Farm",
                "DL-A01",
                "Robusta coffee",
                "TR4",
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2026, 7, 22),
                "GRADE_A",
                new BigDecimal("90.500"),
                "Organic fertilizer and drip irrigation"
        );
    }
}
