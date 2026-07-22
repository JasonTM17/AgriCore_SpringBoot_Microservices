package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class HarvestCompletedKafkaListenerTest {

    private TraceabilityApplicationService traceabilityService;
    private HarvestCompletedKafkaListener listener;

    @BeforeEach
    void setUp() {
        traceabilityService = mock(TraceabilityApplicationService.class);
        listener = new HarvestCompletedKafkaListener(
                traceabilityService,
                new HarvestCompletedEventParser(new ObjectMapper())
        );
    }

    @Test
    void processesAnExactVersionedEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID harvestId = UUID.randomUUID();

        listener.onMessage(validEnvelope(eventId, harvestId));

        ArgumentCaptor<CreateTraceabilityRequest> request =
                ArgumentCaptor.forClass(CreateTraceabilityRequest.class);
        verify(traceabilityService).createFromHarvest(request.capture());
        assertThat(request.getValue().eventId()).isEqualTo(eventId);
        assertThat(request.getValue().harvestBatchId()).isEqualTo(harvestId);
        assertThat(request.getValue().productName()).isEqualTo("Robusta coffee");
        assertThat(request.getValue().farmName()).isEqualTo("Farm");
        assertThat(request.getValue().netWeightKg()).isEqualByComparingTo("90.5");
    }

    @Test
    void ignoresAnotherStructurallyValidEventType() {
        String raw = validEnvelope(UUID.randomUUID(), UUID.randomUUID())
                .replace("HarvestCompleted.v1", "HarvestStarted.v1");

        listener.onMessage(raw);

        verifyNoInteractions(traceabilityService);
    }

    @ParameterizedTest
    @MethodSource("invalidEnvelopes")
    void rejectsInvalidHarvestContractsWithoutCallingTheService(String raw) {
        assertThatThrownBy(() -> listener.onMessage(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");

        verifyNoInteractions(traceabilityService);
    }

    @Test
    void wrapsRetryableProjectionFailures() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(traceabilityService)
                .createFromHarvest(any());

        assertThatThrownBy(() -> listener.onMessage(
                validEnvelope(UUID.randomUUID(), UUID.randomUUID())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Traceability harvest event processing failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static Stream<String> invalidEnvelopes() {
        String valid = validEnvelope(UUID.randomUUID(), UUID.randomUUID());
        return Stream.of(
                "not-json",
                valid.replace("\"eventVersion\":1", "\"eventVersion\":2"),
                valid.replace("HarvestCompleted.v1", "HarvestCompleted.v9"),
                valid.replace("\"producer\":\"harvest-service\"", "\"producer\":\"other-service\""),
                valid.replace("\"harvestDate\":\"2026-07-22\",", ""),
                valid.replace("\"producer\":\"harvest-service\"", "\"producer\":\"harvest-service\",\"unknown\":true"),
                valid.replace("\"productName\":\"Robusta coffee\"", "\"productName\":\"Robusta coffee\",\"unknown\":true"),
                valid.replace("\"productName\":\"Robusta coffee\"", "\"productName\":\"" + "x".repeat(201) + "\"")
        );
    }

    private static String validEnvelope(UUID eventId, UUID harvestId) {
        return """
                {
                  "eventId":"%s",
                  "eventType":"HarvestCompleted.v1",
                  "eventVersion":1,
                  "occurredAt":"2026-07-22T05:00:00Z",
                  "producer":"harvest-service",
                  "payload":{
                    "harvestId":"%s",
                    "harvestBatchId":"%s",
                    "cropCycleId":"%s",
                    "plotId":"%s",
                    "warehouseId":"%s",
                    "productCode":"COFFEE-ROBUSTA",
                    "grossWeightKg":100.0,
                    "netWeightKg":90.5,
                    "qualityGrade":"GRADE_A",
                    "harvestDate":"2026-07-22",
                    "productName":"Robusta coffee"
                  }
                }
                """.formatted(
                eventId,
                harvestId,
                harvestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
