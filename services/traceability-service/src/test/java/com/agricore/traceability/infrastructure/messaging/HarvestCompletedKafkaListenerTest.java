package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies the exact producer contract that builds the public QR read model. Invalid harvest
 * envelopes must fail before the service is called; unrelated versioned events remain ignorable.
 */
class HarvestCompletedKafkaListenerTest {

    private static final UUID EVENT_ID =
            UUID.fromString("a1b2c3d4-1111-4a2b-8c3d-9e0f1a2b3c4d");
    private static final UUID HARVEST_ID =
            UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID CROP_CYCLE_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PLOT_ID =
            UUID.fromString("22222222-3333-4444-5555-666666666666");

    /**
     * Every field the current harvest producer emits when optional presentation data is present.
     */
    private static final String FULL_ENVELOPE = """
            {
              "eventId":"a1b2c3d4-1111-4a2b-8c3d-9e0f1a2b3c4d",
              "eventType":"HarvestCompleted.v1",
              "eventVersion":1,
              "occurredAt":"2026-07-26T10:15:30Z",
              "producer":"harvest-service",
              "payload":{
                "harvestId":"55555555-6666-7777-8888-999999999999",
                "harvestBatchId":"55555555-6666-7777-8888-999999999999",
                "farmId":"44444444-5555-6666-7777-888888888888",
                "cropCycleId":"11111111-2222-3333-4444-555555555555",
                "plotId":"22222222-3333-4444-5555-666666666666",
                "warehouseId":"33333333-4444-5555-6666-777777777777",
                "productCode":"COFFEE-ROBUSTA",
                "grossWeightKg":3500.000,
                "netWeightKg":3300.500,
                "qualityGrade":"GRADE_A",
                "harvestDate":"2026-03-15",
                "farmName":"Nong trai Dak Lak",
                "plotCode":"DL-A01",
                "productName":"Ca phe Robusta",
                "careSummary":"Organic fertilizer, drip irrigation"
              }
            }
            """;

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
    void mapsTheProducerEnvelopeFieldForField() {
        listener.onMessage(FULL_ENVELOPE);

        CreateTraceabilityRequest request = captured();
        assertThat(request.eventId()).isEqualTo(EVENT_ID);
        assertThat(request.harvestBatchId()).isEqualTo(HARVEST_ID);
        assertThat(request.cropCycleId()).isEqualTo(CROP_CYCLE_ID);
        assertThat(request.plotId()).isEqualTo(PLOT_ID);
        assertThat(request.farmName()).isEqualTo("Nong trai Dak Lak");
        assertThat(request.plotCode()).isEqualTo("DL-A01");
        assertThat(request.productName()).isEqualTo("Ca phe Robusta");
        assertThat(request.varietyName()).isNull();
        assertThat(request.plantingDate()).isNull();
        assertThat(request.harvestDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(request.qualityGrade()).isEqualTo("GRADE_A");
        assertThat(request.netWeightKg()).isEqualByComparingTo("3300.500");
        assertThat(request.careSummary()).isEqualTo("Organic fertilizer, drip irrigation");
        assertThat(request.productCode()).isEqualTo("COFFEE-ROBUSTA");
        assertThat(request.grossWeightKg()).isEqualByComparingTo("3500.000");
    }

    @Test
    void omittedOptionalPresentationFieldsRemainNull() {
        listener.onMessage(withoutFields(FULL_ENVELOPE, "farmName", "plotCode", "careSummary"));

        CreateTraceabilityRequest request = captured();
        assertThat(request.farmName()).isNull();
        assertThat(request.plotCode()).isNull();
        assertThat(request.careSummary()).isNull();
    }

    @Test
    void rejectsDivergentHarvestIdentifiers() {
        assertInvalid(FULL_ENVELOPE.replace(
                "\"harvestId\":\"55555555-6666-7777-8888-999999999999\"",
                "\"harvestId\":\"deadbeef-0000-0000-0000-000000000000\""));
    }

    @Test
    void rejectsMissingProductNameInsteadOfFallingBackToProductCode() {
        assertInvalid(withoutFields(FULL_ENVELOPE, "productName"));
    }

    @Test
    void rejectsMissingProductIdentityInsteadOfUsingAPlaceholder() {
        assertInvalid(withoutFields(FULL_ENVELOPE, "productName", "productCode"));
    }

    @Test
    void rejectsBlankOptionalStringsInsteadOfSubstitutingAPlaceholder() {
        assertInvalid(FULL_ENVELOPE.replace(
                "\"farmName\":\"Nong trai Dak Lak\"",
                "\"farmName\":\"   \""));
    }

    @Test
    void rejectsMissingHarvestDateInsteadOfDefaultingToToday() {
        assertInvalid(withoutFields(FULL_ENVELOPE, "harvestDate"));
    }

    @Test
    void rejectsMissingRequiredNumbersAndGradeInsteadOfLeavingThemNull() {
        assertInvalid(withoutFields(
                FULL_ENVELOPE,
                "grossWeightKg",
                "netWeightKg",
                "qualityGrade"
        ));
    }

    @Test
    void ignoresAnotherStructurallyValidEventType() {
        listener.onMessage(FULL_ENVELOPE.replace(
                "HarvestCompleted.v1",
                "HarvestStarted.v1"
        ));

        verifyNoInteractions(traceabilityService);
    }

    @ParameterizedTest
    @MethodSource("invalidEnvelopes")
    void rejectsInvalidHarvestContractsWithoutCallingTheService(String raw) {
        assertInvalid(raw);
    }

    @Test
    void wrapsRetryableProjectionFailures() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(traceabilityService)
                .createFromHarvest(any());

        assertThatThrownBy(() -> listener.onMessage(FULL_ENVELOPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Traceability harvest event processing failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> listener.onMessage(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");

        verifyNoInteractions(traceabilityService);
    }

    private CreateTraceabilityRequest captured() {
        ArgumentCaptor<CreateTraceabilityRequest> captor =
                ArgumentCaptor.forClass(CreateTraceabilityRequest.class);
        verify(traceabilityService).createFromHarvest(captor.capture());
        return captor.getValue();
    }

    private static Stream<String> invalidEnvelopes() {
        return Stream.of(
                "not-json",
                FULL_ENVELOPE.replace("\"eventVersion\":1", "\"eventVersion\":2"),
                FULL_ENVELOPE.replace("HarvestCompleted.v1", "HarvestCompleted.v9"),
                FULL_ENVELOPE.replace(
                        "\"producer\":\"harvest-service\"",
                        "\"producer\":\"other-service\""
                ),
                withoutFields(FULL_ENVELOPE, "eventId"),
                withoutFields(FULL_ENVELOPE, "eventType"),
                """
                        {
                          "eventId":"a1b2c3d4-1111-4a2b-8c3d-9e0f1a2b3c4d",
                          "eventType":"HarvestCompleted.v1",
                          "eventVersion":1,
                          "occurredAt":"2026-07-26T10:15:30Z",
                          "producer":"harvest-service"
                        }
                        """,
                withoutFields(FULL_ENVELOPE, "farmId"),
                FULL_ENVELOPE.replace(
                        "\"harvestBatchId\":\"55555555-6666-7777-8888-999999999999\"",
                        "\"harvestBatchId\":\"not-a-uuid\""
                ),
                FULL_ENVELOPE.replace(
                        "\"producer\":\"harvest-service\"",
                        "\"producer\":\"harvest-service\",\"unknown\":true"
                ),
                FULL_ENVELOPE.replace(
                        "\"productName\":\"Ca phe Robusta\"",
                        "\"productName\":\"Ca phe Robusta\",\"unknown\":true"
                ),
                FULL_ENVELOPE.replace(
                        "\"productName\":\"Ca phe Robusta\"",
                        "\"productName\":\"" + "x".repeat(201) + "\""
                )
        );
    }

    private static String withoutFields(String raw, String... fields) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode envelope = (ObjectNode) objectMapper.readTree(raw);
            ObjectNode payload = envelope.has("payload")
                    ? (ObjectNode) envelope.get("payload")
                    : null;
            for (String field : fields) {
                if (envelope.has(field)) {
                    envelope.remove(field);
                } else if (payload != null) {
                    payload.remove(field);
                }
            }
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new AssertionError("Test fixture must be valid JSON", ex);
        }
    }
}
