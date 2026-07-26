package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.agricore.traceability.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HarvestCompletedProjectionIntegrationTest {

    @Autowired
    private TraceabilityApplicationService traceabilityService;
    @Autowired
    private TraceabilityBatchJpaRepository batchRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearPersistence() {
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        batchRepository.deleteAll();
    }

    @Test
    void authoritativeHarvestEvent_createsPublicProjection() {
        UUID eventId = UUID.randomUUID();
        UUID harvestBatchId = UUID.randomUUID();
        HarvestCompletedKafkaListener listener = new HarvestCompletedKafkaListener(
                traceabilityService,
                new HarvestCompletedEventParser(objectMapper)
        );

        listener.onMessage(validEnvelope(eventId, harvestBatchId));

        var batch = batchRepository.findFirstByHarvestBatchId(harvestBatchId).orElseThrow();
        PublicTraceabilityResponse projection =
                traceabilityService.getPublic(batch.getTraceabilityCode());
        assertThat(projection.productName()).isEqualTo("Robusta coffee");
        assertThat(projection.productCode()).isEqualTo("COFFEE-ROBUSTA");
        assertThat(projection.farmName()).isEqualTo("Authoritative Farm");
        assertThat(projection.plotCode()).isEqualTo("PLOT-A1");
        assertThat(processedEventRepository.findCanonicalOrLegacy(
                eventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        )).isPresent();
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    private static String validEnvelope(UUID eventId, UUID harvestBatchId) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"HarvestCompleted.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-26T00:00:00Z",
                  "producer":"harvest-service",
                  "payload":{
                    "harvestId":"%s",
                    "harvestBatchId":"%s",
                    "farmId":"%s",
                    "cropCycleId":"%s",
                    "plotId":"%s",
                    "warehouseId":"%s",
                    "productCode":"COFFEE-ROBUSTA",
                    "grossWeightKg":100.0,
                    "netWeightKg":90.5,
                    "qualityGrade":"GRADE_A",
                    "harvestDate":"2026-07-26",
                    "farmName":"Authoritative Farm",
                    "plotCode":"PLOT-A1",
                    "productName":"Robusta coffee",
                    "careSummary":"Harvest-owned provenance"
                  }
                }
                """.formatted(
                eventId,
                harvestBatchId,
                harvestBatchId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
