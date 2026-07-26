package com.agricore.farm.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.api.response.PlotResponse;
import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What lands in the outbox, and when.
 *
 * <p>The condition guarding {@code PlotStatusChanged.v1} is
 * {@code request.status() != null && previous != plot.getStatus()} — two branches, neither
 * previously executed. Both halves matter and they fail in opposite directions: drop the null
 * check and a rename emits a status-change event; drop the comparison and re-sending the current
 * status emits one for a change that did not happen. Either way consumers act on a state
 * transition the platform never made, and every response stays 200.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlotStatusChangeEventTest {

    @Autowired
    private FarmApplicationService service;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void creatingAFarmAndPlotEnqueuesOneEventEach() {
        FarmResponse farm = service.createFarm(farmRequest());
        PlotResponse plot = service.createPlot(farm.id(), plotRequest());

        assertThat(eventsFor(farm.id().toString()))
                .extracting(OutboxEventEntity::getEventType)
                .containsExactly(EventTypes.FARM_CREATED);
        assertThat(eventsFor(plot.id().toString()))
                .extracting(OutboxEventEntity::getEventType)
                .containsExactly(EventTypes.PLOT_CREATED);
    }

    @Test
    void changingThePlotStatusEnqueuesTheTransition() {
        PlotResponse plot = newPlot();
        assertThat(plot.status()).isEqualTo("AVAILABLE");

        service.updatePlot(plot.id(), statusPatch("IN_USE"));

        List<OutboxEventEntity> events = eventsFor(plot.id().toString());
        assertThat(events).extracting(OutboxEventEntity::getEventType)
                .containsExactly(EventTypes.PLOT_CREATED, EventTypes.PLOT_STATUS_CHANGED);

        JsonNode payload = payloadOf(events.get(1));
        assertThat(payload.get("status").asText()).isEqualTo("IN_USE");
        assertThat(payload.get("previousStatus").asText())
                .as("a consumer needs the transition, not just the new state")
                .isEqualTo("AVAILABLE");
    }

    /**
     * The half that a naive implementation gets wrong: re-sending the status a plot already has is
     * not a transition, and must not enqueue one.
     */
    @Test
    void resendingTheSameStatusEnqueuesNothing() {
        PlotResponse plot = newPlot();
        service.updatePlot(plot.id(), statusPatch("IN_USE"));
        int afterRealChange = eventsFor(plot.id().toString()).size();

        service.updatePlot(plot.id(), statusPatch("IN_USE"));
        service.updatePlot(plot.id(), statusPatch("in_use"));

        assertThat(eventsFor(plot.id().toString()))
                .as("a no-op status write is not a state transition")
                .hasSize(afterRealChange);
    }

    /**
     * The other half: an update that never mentions status cannot emit a status change, however
     * many other fields it touches.
     */
    @Test
    void updatingOtherFieldsEnqueuesNothing() {
        PlotResponse plot = newPlot();
        int afterCreate = eventsFor(plot.id().toString()).size();

        service.updatePlot(plot.id(), new UpdatePlotRequest(
                "Renamed block", new BigDecimal("9.75"), "CLAY", null, 11.0, 107.0));

        assertThat(eventsFor(plot.id().toString())).hasSize(afterCreate);
    }

    /**
     * Farm updates have no event at all. A spurious event is as much of a defect as a missing one
     * for a consumer keyed on it.
     */
    @Test
    void updatingAFarmEnqueuesNothing() {
        FarmResponse farm = service.createFarm(farmRequest());
        int afterCreate = eventsFor(farm.id().toString()).size();

        service.updateFarm(farm.id(),
                new UpdateFarmRequest("Renamed", null, null, null, null, null, "INACTIVE"));

        assertThat(eventsFor(farm.id().toString())).hasSize(afterCreate);
    }

    /**
     * The outbox row is written inside the same transaction as the domain change — that is the
     * whole point of the pattern. If it were not, the event would be visible only after a separate
     * commit that could fail.
     */
    @Test
    void theEventCarriesTheStandardEnvelope() {
        FarmResponse farm = service.createFarm(farmRequest());

        JsonNode envelope = envelopeOf(eventsFor(farm.id().toString()).get(0));

        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.FARM_CREATED);
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("producer").asText()).isEqualTo("farm-service");
        assertThat(envelope.get("payload").get("farmId").asText()).isEqualTo(farm.id().toString());
    }

    private PlotResponse newPlot() {
        return service.createPlot(service.createFarm(farmRequest()).id(), plotRequest());
    }

    private List<OutboxEventEntity> eventsFor(String aggregateId) {
        return outboxRepository.findAll().stream()
                .filter(e -> aggregateId.equals(e.getAggregateId()))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();
    }

    private JsonNode envelopeOf(OutboxEventEntity event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception ex) {
            throw new AssertionError("outbox payload is not JSON: " + event.getPayload(), ex);
        }
    }

    private JsonNode payloadOf(OutboxEventEntity event) {
        return envelopeOf(event).get("payload");
    }

    private static UpdatePlotRequest statusPatch(String status) {
        return new UpdatePlotRequest(null, null, null, status, null, null);
    }

    private static CreateFarmRequest farmRequest() {
        return new CreateFarmRequest(
                "EVT-" + System.nanoTime(), "Event Farm", "Dak Lak", "Dak Lak",
                new BigDecimal("10.5"), 12.6667, 108.05);
    }

    private static CreatePlotRequest plotRequest() {
        return new CreatePlotRequest(
                "P-EVT", "Event Block", new BigDecimal("2.5"), "BASALT", null, 12.67, 108.05);
    }
}
