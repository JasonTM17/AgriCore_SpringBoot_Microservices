package com.agricore.assistant.api.streaming;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.service.GenerationEventReplayService;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationEventStreamProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class GenerationEventStreamService {

    private final GenerationEventReplayService replayService;
    private final TaskScheduler scheduler;
    private final AssistantGenerationEventStreamProperties properties;
    private final Clock clock;
    private final Semaphore connectionSlots;

    public GenerationEventStreamService(
            GenerationEventReplayService replayService,
            @Qualifier("assistantEventStreamScheduler") TaskScheduler scheduler,
            AssistantGenerationEventStreamProperties properties,
            Clock clock
    ) {
        this.replayService = replayService;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
        this.connectionSlots = new Semaphore(properties.getMaxConnections(), true);
    }

    public SseEmitter open(
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long afterSequence
    ) {
        GenerationEventReplayBatch initialBatch = replayService.read(
                actor, conversationId, generationId, afterSequence, properties.getBatchSize());
        if (!connectionSlots.tryAcquire()) {
            throw AssistantException.streamCapacityExceeded();
        }

        SseEmitter emitter = new SseEmitter(properties.getMaxConnectionDuration().toMillis());
        GenerationEventStreamSession session = new GenerationEventStreamSession(
                replayService,
                scheduler,
                properties,
                clock,
                emitter,
                actor,
                conversationId,
                generationId,
                afterSequence,
                connectionSlots::release
        );
        emitter.onCompletion(session::close);
        emitter.onTimeout(session::complete);
        emitter.onError(ignored -> session.close());
        session.start(initialBatch);
        return emitter;
    }
}
