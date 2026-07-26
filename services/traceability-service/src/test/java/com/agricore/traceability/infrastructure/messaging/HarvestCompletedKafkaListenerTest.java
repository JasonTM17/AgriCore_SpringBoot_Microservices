package com.agricore.traceability.infrastructure.messaging;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The consumer that builds the public QR read model. It had no test: 0 of 24 branches covered,
 * on the path that feeds the one page an end consumer scans from a printed label.
 *
 * <p>{@link #FULL_ENVELOPE} is the exact shape {@code HarvestApplicationService} emits, field for
 * field, so a producer rename fails here rather than silently on the real topic — where the failure
 * mode is a batch that quietly never gets a QR entry.
 */
class HarvestCompletedKafkaListenerTest {

    /** Every field the producer emits when the operator supplied the optional ones. */
    private static final String FULL_ENVELOPE = """
            {
              "eventId": "a1b2c3d4-1111-4a2b-8c3d-9e0f1a2b3c4d",
              "eventType": "HarvestCompleted.v1",
              "eventVersion": 1,
              "occurredAt": "2026-07-26T10:15:30Z",
              "producer": "harvest-service",
              "payload": {
                "harvestId": "55555555-6666-7777-8888-999999999999",
                "harvestBatchId": "55555555-6666-7777-8888-999999999999",
                "cropCycleId": "11111111-2222-3333-4444-555555555555",
                "plotId": "22222222-3333-4444-5555-666666666666",
                "warehouseId": "33333333-4444-5555-6666-777777777777",
                "productCode": "COFFEE-ROBUSTA",
                "grossWeightKg": 3500.000,
                "netWeightKg": 3300.500,
                "qualityGrade": "GRADE_A",
                "harvestDate": "2026-03-15",
                "farmName": "Nong trai Dak Lak",
                "plotCode": "DL-A01",
                "productName": "Ca phe Robusta",
                "careSummary": "Organic fertilizer, drip irrigation"
              }
            }
            """;

    /**
     * The producer omits {@code farmName}, {@code plotCode}, and {@code careSummary} entirely when
     * the operator leaves them blank — it does not send empty strings. That omission is the real
     * case the consumer's fallbacks exist for.
     */
    private static final String MINIMAL_ENVELOPE = """
            {
              "eventId": "b2c3d4e5-2222-4b3c-9d4e-0f1a2b3c4d5e",
              "eventType": "HarvestCompleted.v1",
              "eventVersion": 1,
              "occurredAt": "2026-07-26T11:00:00Z",
              "producer": "harvest-service",
              "payload": {
                "harvestBatchId": "66666666-7777-8888-9999-000000000000",
                "cropCycleId": "11111111-2222-3333-4444-555555555555",
                "plotId": "22222222-3333-4444-5555-666666666666",
                "productCode": "RICE-ST25",
                "productName": "Gao ST25",
                "netWeightKg": 1200.000,
                "qualityGrade": "GRADE_B",
                "harvestDate": "2026-04-20"
              }
            }
            """;

    private final TraceabilityApplicationService service = mock(TraceabilityApplicationService.class);
    private final HarvestCompletedKafkaListener listener =
            new HarvestCompletedKafkaListener(service, new ObjectMapper());

    @Test
    void mapsTheProducerEnvelopeFieldForField() {
        listener.onMessage(FULL_ENVELOPE);

        CreateTraceabilityRequest request = captured();
        assertThat(request.eventId()).isEqualTo("a1b2c3d4-1111-4a2b-8c3d-9e0f1a2b3c4d");
        assertThat(request.harvestBatchId())
                .isEqualTo(UUID.fromString("55555555-6666-7777-8888-999999999999"));
        assertThat(request.cropCycleId())
                .isEqualTo(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        assertThat(request.plotId())
                .isEqualTo(UUID.fromString("22222222-3333-4444-5555-666666666666"));
        assertThat(request.farmName()).isEqualTo("Nong trai Dak Lak");
        assertThat(request.plotCode()).isEqualTo("DL-A01");
        assertThat(request.productName()).isEqualTo("Ca phe Robusta");
        assertThat(request.harvestDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(request.qualityGrade()).isEqualTo("GRADE_A");
        assertThat(request.netWeightKg()).isEqualByComparingTo("3300.500");
        assertThat(request.careSummary()).isEqualTo("Organic fertilizer, drip irrigation");
    }

    /**
     * {@code harvestBatchId} is the key the whole read model hangs off, and the traceability code is
     * derived from it. Reading {@code harvestId} instead — the producer emits both, with the same
     * value today — would work until they diverge.
     */
    @Test
    void keysOnHarvestBatchIdRatherThanHarvestId() {
        listener.onMessage(FULL_ENVELOPE.replace(
                "\"harvestId\": \"55555555-6666-7777-8888-999999999999\"",
                "\"harvestId\": \"deadbeef-0000-0000-0000-000000000000\""));

        assertThat(captured().harvestBatchId())
                .isEqualTo(UUID.fromString("55555555-6666-7777-8888-999999999999"));
    }

    @Test
    void substitutesPlaceholdersForTheFieldsTheProducerOmits() {
        listener.onMessage(MINIMAL_ENVELOPE);

        CreateTraceabilityRequest request = captured();
        assertThat(request.farmName()).isEqualTo("Farm");
        assertThat(request.plotCode()).isEqualTo("PLOT");
        assertThat(request.careSummary()).isEqualTo("See farm records");
    }

    /**
     * Compatibility shim for an event predating {@code productName}. The field is
     * {@code @NotBlank} downstream, so falling back to the code keeps an old event processable
     * instead of failing validation.
     */
    @Test
    void fallsBackFromProductNameToProductCode() {
        listener.onMessage(MINIMAL_ENVELOPE.replace("\"productName\": \"Gao ST25\",", ""));

        assertThat(captured().productName()).isEqualTo("RICE-ST25");
    }

    @Test
    void fallsBackToAPlaceholderWhenNeitherProductFieldIsPresent() {
        listener.onMessage(MINIMAL_ENVELOPE
                .replace("\"productName\": \"Gao ST25\",", "")
                .replace("\"productCode\": \"RICE-ST25\",", ""));

        assertThat(captured().productName()).isEqualTo("PRODUCT");
    }

    /**
     * A blank string is treated as absent, not carried through. Otherwise the QR page renders an
     * empty farm name where the placeholder was intended.
     */
    @Test
    void treatsBlankStringsAsAbsent() {
        listener.onMessage(FULL_ENVELOPE.replace(
                "\"farmName\": \"Nong trai Dak Lak\"", "\"farmName\": \"   \""));

        assertThat(captured().farmName()).isEqualTo("Farm");
    }

    /**
     * Documents a real gap rather than asserting a wish: the producer never emits
     * {@code varietyName} or {@code plantingDate}, so both arrive null on every event-driven batch
     * and the public QR page shows them empty. Only the REST backfill can populate them today.
     * Recorded in docs/project-roadmap.md.
     */
    @Test
    void varietyAndPlantingDateArriveNullFromThisProducer() {
        listener.onMessage(FULL_ENVELOPE);

        CreateTraceabilityRequest request = captured();
        assertThat(request.varietyName()).isNull();
        assertThat(request.plantingDate()).isNull();
    }

    @Test
    void defaultsAMissingHarvestDateToToday() {
        listener.onMessage(MINIMAL_ENVELOPE.replace("\"harvestDate\": \"2026-04-20\"", "\"unused\": 1"));

        assertThat(captured().harvestDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void leavesOptionalNumbersAndGradesNullWhenAbsent() {
        listener.onMessage(MINIMAL_ENVELOPE
                .replace("\"netWeightKg\": 1200.000,", "")
                .replace("\"qualityGrade\": \"GRADE_B\",", ""));

        CreateTraceabilityRequest request = captured();
        assertThat(request.netWeightKg()).isNull();
        assertThat(request.qualityGrade()).isNull();
    }

    @Test
    void ignoresAnEventFromAnotherProducer() {
        listener.onMessage("""
                {"eventId": "x", "eventType": "FarmCreated.v1", "payload": {"farmId": "1"}}
                """);

        verify(service, never()).createFromHarvest(any());
    }

    @Test
    void ignoresAnEnvelopeWithNoEventType() {
        listener.onMessage("""
                {"eventId": "x", "payload": {"harvestBatchId": "55555555-6666-7777-8888-999999999999"}}
                """);

        verify(service, never()).createFromHarvest(any());
    }

    /**
     * Without an event id the consumer cannot deduplicate, so processing would double-write on a
     * redelivery. Dropping the message is the safe read: it is skipped, not sent to the DLT.
     */
    @Test
    void ignoresAnEnvelopeWithNoEventId() {
        listener.onMessage("""
                {"eventType": "HarvestCompleted.v1",
                 "payload": {"harvestBatchId": "55555555-6666-7777-8888-999999999999"}}
                """);

        verify(service, never()).createFromHarvest(any());
    }

    @Test
    void ignoresAnEnvelopeWithNoPayload() {
        listener.onMessage("""
                {"eventId": "x", "eventType": "HarvestCompleted.v1"}
                """);

        verify(service, never()).createFromHarvest(any());
    }

    @Test
    void throwsOnMalformedJsonSoTheErrorHandlerRoutesToTheDlt() {
        assertThatThrownBy(() -> listener.onMessage("not json at all"))
                .isInstanceOf(IllegalStateException.class);

        verify(service, never()).createFromHarvest(any());
    }

    /**
     * An unparseable batch id must reach the DLT rather than be dropped. A silently skipped harvest
     * is a batch with no traceability entry and nothing recording that it is missing.
     */
    @Test
    void throwsOnAnUnparseableHarvestBatchId() {
        assertThatThrownBy(() -> listener.onMessage(
                MINIMAL_ENVELOPE.replace("66666666-7777-8888-9999-000000000000", "not-a-uuid")))
                .isInstanceOf(IllegalStateException.class);

        verify(service, never()).createFromHarvest(any());
    }

    private CreateTraceabilityRequest captured() {
        ArgumentCaptor<CreateTraceabilityRequest> captor =
                ArgumentCaptor.forClass(CreateTraceabilityRequest.class);
        verify(service).createFromHarvest(captor.capture());
        return captor.getValue();
    }
}
