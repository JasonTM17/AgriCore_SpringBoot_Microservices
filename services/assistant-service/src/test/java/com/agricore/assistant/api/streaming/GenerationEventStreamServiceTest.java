package com.agricore.assistant.api.streaming;

import com.agricore.assistant.application.model.GenerationEventReplayBatch;
import com.agricore.assistant.application.service.GenerationEventReplayService;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.infrastructure.configuration.AssistantGenerationEventStreamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationEventStreamServiceTest {

    @Test
    void rejectsExcessStreamsBeforeReadingReplayState() {
        GenerationEventReplayService replayService = mock(GenerationEventReplayService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AssistantGenerationEventStreamProperties properties =
                new AssistantGenerationEventStreamProperties();
        properties.setMaxConnections(1);
        when(replayService.read(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(new GenerationEventReplayBatch(List.of(), 0, false));
        doReturn(scheduledFuture)
                .when(scheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        GenerationEventStreamService service = new GenerationEventStreamService(
                replayService,
                scheduler,
                properties,
                Clock.systemUTC()
        );
        AssistantActor actor = new AssistantActor(
                UUID.randomUUID(),
                List.of("FIELD_WORKER")
        );
        UUID conversationId = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();

        SseEmitter first = service.open(actor, conversationId, generationId, 0);

        assertThatThrownBy(() -> service.open(
                actor,
                UUID.randomUUID(),
                UUID.randomUUID(),
                0
        ))
                .isInstanceOf(AssistantException.class)
                .extracting("code", "httpStatus")
                .containsExactly("GENERATION_STREAM_CAPACITY_EXCEEDED", 503);
        verify(replayService, times(1))
                .read(actor, conversationId, generationId, 0, properties.getBatchSize());
        first.complete();
    }
}
