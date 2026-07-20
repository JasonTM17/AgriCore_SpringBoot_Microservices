package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.GenerationEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationEventReplayBatchTest {

    private static final UUID GENERATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

    @Test
    void acceptsContiguousReplayAndReportsCaughtUpState() {
        GenerationEventReplayBatch batch = GenerationEventReplayBatch.validated(
                List.of(event(1), event(2)), 3, true, 0);

        assertThat(batch.events()).extracting(AssistantGenerationEvent::sequenceNo)
                .containsExactly(1L, 2L);
        assertThat(batch.caughtUp(2)).isTrue();
        assertThat(batch.caughtUp(1)).isFalse();
    }

    @Test
    void rejectsCursorAheadOfPersistedStream() {
        assertThatThrownBy(() -> GenerationEventReplayBatch.validated(List.of(), 2, false, 2))
                .isInstanceOfSatisfying(AssistantException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_EVENT_CURSOR");
                    assertThat(exception.getHttpStatus()).isEqualTo(400);
                });
    }

    @Test
    void rejectsReplayWhenExpectedEventHasExpired() {
        assertThatThrownBy(() -> GenerationEventReplayBatch.validated(List.of(event(2)), 3, true, 0))
                .isInstanceOfSatisfying(AssistantException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("GENERATION_EVENT_REPLAY_EXPIRED");
                    assertThat(exception.getHttpStatus()).isEqualTo(410);
                });
    }

    @Test
    void rejectsInternalSequenceGap() {
        assertThatThrownBy(() -> GenerationEventReplayBatch.validated(
                List.of(event(1), event(3)), 4, true, 0))
                .isInstanceOfSatisfying(AssistantException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("GENERATION_EVENT_REPLAY_EXPIRED"));
    }

    @Test
    void acceptsEmptyBatchWhenCursorAlreadyReachedLatestEvent() {
        GenerationEventReplayBatch batch = GenerationEventReplayBatch.validated(List.of(), 3, true, 2);

        assertThat(batch.events()).isEmpty();
        assertThat(batch.caughtUp(2)).isTrue();
    }

    private static AssistantGenerationEvent event(long sequence) {
        return new AssistantGenerationEvent(
                UUID.randomUUID(), GENERATION_ID, sequence, GenerationEventType.DELTA,
                "{\"delta\":\"safe\"}", NOW, NOW.plusSeconds(60)
        );
    }
}
