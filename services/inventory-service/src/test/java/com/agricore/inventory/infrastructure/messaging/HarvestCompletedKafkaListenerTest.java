package com.agricore.inventory.infrastructure.messaging;

import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

    private InventoryApplicationService inventoryService;
    private HarvestCompletedKafkaListener listener;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryApplicationService.class);
        listener = new HarvestCompletedKafkaListener(
                inventoryService,
                new HarvestCompletedEventParser(new ObjectMapper())
        );
    }

    @Test
    void processesAnExactVersionedEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID harvestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        listener.onMessage(validEnvelope(eventId, harvestId, warehouseId));

        ArgumentCaptor<HarvestCompletedCommand> command = ArgumentCaptor.forClass(HarvestCompletedCommand.class);
        verify(inventoryService).processHarvestCompleted(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo(eventId.toString());
        assertThat(command.getValue().harvestBatchId()).isEqualTo(harvestId);
        assertThat(command.getValue().warehouseId()).isEqualTo(warehouseId);
        assertThat(command.getValue().netWeightKg()).isEqualByComparingTo("90.5");
    }

    @Test
    void ignoresAnotherStructurallyValidEventType() {
        String raw = validEnvelope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .replace("HarvestCompleted.v1", "HarvestStarted.v1");

        listener.onMessage(raw);

        verifyNoInteractions(inventoryService);
    }

    @ParameterizedTest
    @MethodSource("invalidEnvelopes")
    void rejectsInvalidHarvestContractsWithoutCallingTheService(String raw) {
        assertThatThrownBy(() -> listener.onMessage(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");

        verifyNoInteractions(inventoryService);
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("uncoveredContractViolations")
    void rejectsUncoveredContractBoundariesAsDirectDltFailures(String raw, String expectedReason) {
        assertThatThrownBy(() -> listener.onMessage(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedReason);

        verifyNoInteractions(inventoryService);
    }

    @Test
    void wrapsRetryableProjectionFailures() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(inventoryService)
                .processHarvestCompleted(any());

        assertThatThrownBy(() -> listener.onMessage(
                validEnvelope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Harvest event processing failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static Stream<String> invalidEnvelopes() {
        String valid = validEnvelope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return Stream.of(
                "",
                "{}",
                valid.replace("\"eventVersion\":1", "\"eventVersion\":2"),
                valid.replace("HarvestCompleted.v1", "HarvestCompleted.v2"),
                valid.replace("\"producer\":\"harvest-service\"", "\"producer\":\"other-service\""),
                valid.replace("\"netWeightKg\":90.5,", ""),
                valid.replace("\"producer\":\"harvest-service\"", "\"producer\":\"harvest-service\",\"unknown\":true"),
                valid.replace("\"productName\":\"Robusta coffee\"", "\"productName\":\"Robusta coffee\",\"unknown\":true"),
                valid.replace("\"productName\":\"Robusta coffee\"", "\"productName\":\"" + "x".repeat(201) + "\"")
        );
    }

    private static Stream<Arguments> uncoveredContractViolations() {
        UUID eventId = UUID.randomUUID();
        UUID harvestId = UUID.randomUUID();
        String valid = validEnvelope(eventId, harvestId, UUID.randomUUID());

        return Stream.of(
                Arguments.of(
                        valid.replace("\"productCode\":\"COFFEE-ROBUSTA\",", ""),
                        "payload.productCode must be a non-blank string"
                ),
                Arguments.of(
                        valid.replace("\"grossWeightKg\":100.0,", ""),
                        "payload.grossWeightKg must be a number"
                ),
                Arguments.of(
                        valid.replace("\"grossWeightKg\":100.0", "\"grossWeightKg\":0"),
                        "payload.grossWeightKg must be greater than zero"
                ),
                Arguments.of(
                        valid.replace("\"netWeightKg\":90.5", "\"netWeightKg\":\"90.5\""),
                        "payload.netWeightKg must be a number"
                ),
                Arguments.of(
                        valid.replace(
                                "\"harvestBatchId\":\"" + harvestId + "\"",
                                "\"harvestBatchId\":\"" + UUID.randomUUID() + "\""
                        ),
                        "harvestId and harvestBatchId must match"
                ),
                Arguments.of(
                        valid.replace("\"harvestDate\":\"2026-07-22\"", "\"harvestDate\":\"2026-07-22T05:00:00Z\""),
                        "payload.harvestDate must be an ISO-8601 date"
                ),
                Arguments.of(
                        valid.replace(
                                "\"productName\":\"Robusta coffee\"",
                                "\"farmName\":\" \",\"productName\":\"Robusta coffee\""
                        ),
                        "payload.farmName must be a non-blank string when present"
                ),
                Arguments.of(
                        valid.replace(
                                "\"productName\":\"Robusta coffee\"",
                                "\"careSummary\":\"" + "x".repeat(1001)
                                        + "\",\"productName\":\"Robusta coffee\""
                        ),
                        "payload.careSummary must be at most 1000 characters"
                )
        );
    }

    private static String validEnvelope(UUID eventId, UUID harvestId, UUID warehouseId) {
        UUID farmId = UUID.randomUUID();
        return validEnvelope(eventId, harvestId, farmId, warehouseId);
    }

    private static String validEnvelope(UUID eventId, UUID harvestId, UUID farmId, UUID warehouseId) {
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
                    "farmId":"%s",
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
                farmId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                warehouseId
        );
    }
}
