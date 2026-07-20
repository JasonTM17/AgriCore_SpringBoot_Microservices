package com.agricore.assistant.api.streaming;

import com.agricore.assistant.domain.exception.AssistantException;
import org.springframework.stereotype.Component;

@Component
public class GenerationEventCursorResolver {

    public long resolve(long afterSequence, String lastEventId) {
        if (afterSequence < -1) {
            throw AssistantException.invalidEventCursor();
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            return afterSequence;
        }
        try {
            long headerSequence = Long.parseLong(lastEventId.strip());
            if (headerSequence < 0) {
                throw AssistantException.invalidEventCursor();
            }
            return Math.max(afterSequence, headerSequence);
        } catch (NumberFormatException exception) {
            throw AssistantException.invalidEventCursor();
        }
    }
}
