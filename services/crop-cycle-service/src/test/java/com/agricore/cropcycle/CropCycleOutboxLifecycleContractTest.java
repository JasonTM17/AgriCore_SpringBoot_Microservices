package com.agricore.cropcycle;

import com.agricore.common.event.EventTypes;
import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.application.service.CropCycleApplicationService;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CropCycleOutboxLifecycleContractTest {

    @Autowired
    private CropCycleApplicationService cycleService;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completion_writesTerminalEventAfterStageChanges() throws Exception {
        CropCycleResponse cycle = createCycle(UUID.randomUUID(), "COMPLETE");

        advance(cycle.id(), "LAND_PREPARATION");
        advance(cycle.id(), "SOWING");
        advance(cycle.id(), "GROWING");
        advance(cycle.id(), "HARVESTING");
        CropCycleResponse completed = advance(cycle.id(), "COMPLETED");

        List<OutboxEventEntity> events = eventsFor(cycle.id());
        assertThat(events)
                .filteredOn(event -> EventTypes.CROP_CYCLE_STAGE_CHANGED.equals(event.getEventType()))
                .hasSize(4);
        assertThat(events).hasSize(6);
        assertTerminalEvent(
                eventFor(events, EventTypes.CROP_CYCLE_COMPLETED),
                "HARVESTING",
                "COMPLETED",
                "COMPLETED"
        );
        assertThat(completed.stage()).isEqualTo("COMPLETED");
        assertThat(completed.status()).isEqualTo("COMPLETED");
    }

    @Test
    void cancellation_writesCancelledEvent() throws Exception {
        CropCycleResponse cycle = createCycle(UUID.randomUUID(), "CANCEL");

        CropCycleResponse cancelled = advance(cycle.id(), "CANCELLED");

        List<OutboxEventEntity> events = eventsFor(cycle.id());
        assertThat(events).hasSize(2);
        assertTerminalEvent(
                eventFor(events, EventTypes.CROP_CYCLE_CANCELLED),
                "PLANNED",
                "CANCELLED",
                "CANCELLED"
        );
        assertThat(cancelled.stage()).isEqualTo("CANCELLED");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void sameStage_writesNoAdditionalEvent() {
        CropCycleResponse cycle = createCycle(UUID.randomUUID(), "NOOP");
        int eventCount = eventsFor(cycle.id()).size();

        CropCycleResponse unchanged = cycleService.changeStage(
                cycle.id(),
                new ChangeStageRequest("PLANNED", "ignored for no-op")
        );

        assertThat(eventsFor(cycle.id())).hasSize(eventCount);
        assertThat(unchanged.stage()).isEqualTo("PLANNED");
        assertThat(unchanged.status()).isEqualTo("DRAFT");
        assertThat(unchanged.notes()).isEqualTo(cycle.notes());
    }

    @Test
    void overlappingCreate_writesNoOutboxEvent() {
        UUID plotId = UUID.randomUUID();
        createCycle(plotId, "OVERLAP-A");
        long outboxCount = outboxRepository.count();

        assertThatThrownBy(() -> createCycle(plotId, "OVERLAP-B"))
                .isInstanceOfSatisfying(CropCycleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("CROP_CYCLE_OVERLAP");
                    assertThat(exception.getHttpStatus()).isEqualTo(409);
                });

        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
    }

    private CropCycleResponse createCycle(UUID plotId, String suffix) {
        return cycleService.create(new CreateCropCycleRequest(
                "OBX-" + suffix + "-" + UUID.randomUUID(),
                UUID.randomUUID(),
                plotId,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 11, 30),
                "contract test"
        ));
    }

    private CropCycleResponse advance(UUID cycleId, String stage) {
        return cycleService.changeStage(cycleId, new ChangeStageRequest(stage, null));
    }

    private List<OutboxEventEntity> eventsFor(UUID cycleId) {
        String aggregateId = cycleId.toString();
        return outboxRepository.findAll().stream()
                .filter(event -> aggregateId.equals(event.getAggregateId()))
                .toList();
    }

    private static OutboxEventEntity eventFor(List<OutboxEventEntity> events, String eventType) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing outbox event " + eventType));
    }

    private void assertTerminalEvent(
            OutboxEventEntity event,
            String previousStage,
            String stage,
            String status
    ) throws Exception {
        assertThat(event.getTopic()).isEqualTo("agricore.crop-cycle.events");
        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo(event.getEventType());
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("crop-cycle-service");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("previousStage").asText()).isEqualTo(previousStage);
        assertThat(payload.get("stage").asText()).isEqualTo(stage);
        assertThat(payload.get("status").asText()).isEqualTo(status);
    }
}
