package com.agricore.assistant.api.streaming;

import com.agricore.assistant.api.response.GenerationEventResponse;
import com.agricore.assistant.api.response.GenerationStreamErrorResponse;
import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.service.GenerationEventReplayService;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationEventStreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class GenerationEventStreamSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationEventStreamSession.class);
    private static final String SAFE_STREAM_ERROR = "GENERATION_STREAM_UNAVAILABLE";

    private final GenerationEventReplayService replayService;
    private final TaskScheduler scheduler;
    private final AssistantGenerationEventStreamProperties properties;
    private final Clock clock;
    private final SseEmitter emitter;
    private final AssistantActor actor;
    private final UUID conversationId;
    private final UUID generationId;
    private final Runnable connectionRelease;
    private final AtomicLong cursor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<ScheduledFuture<?>> scheduledTask = new AtomicReference<>();
    private final Instant deadline;
    private volatile Instant lastWriteAt;

    GenerationEventStreamSession(
            GenerationEventReplayService replayService,
            TaskScheduler scheduler,
            AssistantGenerationEventStreamProperties properties,
            Clock clock,
            SseEmitter emitter,
            AssistantActor actor,
            UUID conversationId,
            UUID generationId,
            long initialCursor,
            Runnable connectionRelease
    ) {
        this.replayService = replayService;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
        this.emitter = emitter;
        this.actor = actor;
        this.conversationId = conversationId;
        this.generationId = generationId;
        this.cursor = new AtomicLong(initialCursor);
        this.connectionRelease = connectionRelease;
        this.lastWriteAt = clock.instant();
        this.deadline = lastWriteAt.plus(properties.getMaxConnectionDuration());
    }

    void start(GenerationEventReplayBatch initialBatch) {
        if (!sendBatch(initialBatch)) {
            return;
        }
        if (initialBatch.terminal() && initialBatch.caughtUp(cursor.get())) {
            complete();
            return;
        }
        try {
            ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                    this::poll,
                    properties.getPollInterval()
            );
            scheduledTask.set(future);
            if (closed.get()) {
                future.cancel(false);
            }
        } catch (RuntimeException exception) {
            failStream(SAFE_STREAM_ERROR, exception);
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> future = scheduledTask.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        connectionRelease.run();
    }

    void complete() {
        if (closed.get()) {
            return;
        }
        close();
        emitter.complete();
    }

    private void poll() {
        if (closed.get()) {
            return;
        }
        if (!clock.instant().isBefore(deadline)) {
            complete();
            return;
        }
        try {
            GenerationEventReplayBatch batch = replayService.read(
                    actor,
                    conversationId,
                    generationId,
                    cursor.get(),
                    properties.getBatchSize()
            );
            if (!sendBatch(batch)) {
                return;
            }
            if (batch.terminal() && batch.caughtUp(cursor.get())) {
                complete();
            } else {
                sendHeartbeatIfDue();
            }
        } catch (AssistantException exception) {
            failStream(exception.getCode(), exception);
        } catch (RuntimeException exception) {
            failStream(SAFE_STREAM_ERROR, exception);
        }
    }

    private boolean sendBatch(GenerationEventReplayBatch batch) {
        for (AssistantGenerationEvent event : batch.events()) {
            if (closed.get()) {
                return false;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.sequenceNo()))
                        .name(event.eventType().name().toLowerCase(Locale.ROOT))
                        .data(GenerationEventResponse.from(event), MediaType.APPLICATION_JSON));
                cursor.set(event.sequenceNo());
                lastWriteAt = clock.instant();
            } catch (IOException | RuntimeException exception) {
                close();
                return false;
            }
        }
        return true;
    }

    private void sendHeartbeatIfDue() {
        Instant now = clock.instant();
        if (Duration.between(lastWriteAt, now).compareTo(properties.getHeartbeatInterval()) < 0) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
            lastWriteAt = now;
        } catch (IOException | RuntimeException exception) {
            close();
        }
    }

    private void failStream(String safeCode, RuntimeException exception) {
        LOGGER.warn(
                "Generation event stream closed generationId={} errorType={}",
                generationId,
                exception.getClass().getSimpleName()
        );
        if (!closed.get()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("stream-error")
                        .data(new GenerationStreamErrorResponse(safeCode), MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException ignored) {
                // The client may already be disconnected; no provider work is cancelled here.
            }
        }
        complete();
    }
}
