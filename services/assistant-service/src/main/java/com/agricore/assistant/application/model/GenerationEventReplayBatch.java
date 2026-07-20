package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;

import java.util.List;

public record GenerationEventReplayBatch(
        List<AssistantGenerationEvent> events,
        long nextEventSequence,
        boolean terminal
) {

    public GenerationEventReplayBatch {
        events = List.copyOf(events);
        if (nextEventSequence < 0) {
            throw new IllegalArgumentException("nextEventSequence must not be negative");
        }
    }

    public static GenerationEventReplayBatch validated(
            List<AssistantGenerationEvent> events,
            long nextEventSequence,
            boolean terminal,
            long afterSequence
    ) {
        if (afterSequence < -1) {
            throw new IllegalArgumentException("afterSequence must be at least -1");
        }
        long latestSequence = nextEventSequence - 1;
        if (afterSequence > latestSequence) {
            throw AssistantException.invalidEventCursor();
        }
        if (hasReplayGap(events, afterSequence, latestSequence)) {
            throw AssistantException.eventReplayExpired();
        }
        return new GenerationEventReplayBatch(events, nextEventSequence, terminal);
    }

    public boolean caughtUp(long afterSequence) {
        return afterSequence >= nextEventSequence - 1;
    }

    private static boolean hasReplayGap(
            List<AssistantGenerationEvent> events,
            long afterSequence,
            long latestSequence
    ) {
        long expectedSequence = afterSequence + 1;
        for (AssistantGenerationEvent event : events) {
            if (event.sequenceNo() != expectedSequence || event.sequenceNo() > latestSequence) {
                return true;
            }
            expectedSequence++;
        }
        return events.isEmpty() && afterSequence < latestSequence;
    }
}
