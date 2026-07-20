package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agricore.assistant.provider.model=test-model",
        "agricore.assistant.streaming.max-connections=1",
        "agricore.assistant.streaming.poll-interval=PT0.1S",
        "agricore.assistant.streaming.heartbeat-interval=PT1S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationEventStreamLifecycleIntegrationTest extends GenerationEventStreamIntegrationTestSupport {

    @Test
    void tailsNewTerminalEventAndSendsHeartbeatWhileGenerationIsActive() throws Exception {
        Fixture fixture = activeGeneration("sse-tail");
        MvcResult stream = openStream(fixture, 0, null);

        TimeUnit.MILLISECONDS.sleep(1_300);
        cancel(fixture);
        stream.getAsyncResult(5_000);

        String content = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(content).contains(":heartbeat", "id:1", "event:cancelled");
    }

    @Test
    void capacityIsBoundedAndDisconnectReleasesSlotWithoutCancellingGeneration() throws Exception {
        Fixture first = activeGeneration("sse-capacity-1");
        Fixture second = activeGeneration("sse-capacity-2");
        MvcResult firstStream = openStream(first, 0, null);

        mockMvc.perform(authenticated(
                        get(second.eventsPath())
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .queryParam("after", "0"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_STREAM_CAPACITY_EXCEEDED"));

        firstStream.getRequest().getAsyncContext().complete();
        MvcResult secondStream = openStream(second, 0, null);
        assertThat(generationStatus(first.generationId())).isEqualTo("QUEUED");
        assertThat(cancelRequestedAt(first.generationId())).isNull();

        secondStream.getRequest().getAsyncContext().complete();
        cancel(first);
        cancel(second);
    }
}
