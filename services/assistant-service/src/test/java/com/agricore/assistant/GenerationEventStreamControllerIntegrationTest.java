package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class GenerationEventStreamControllerIntegrationTest extends GenerationEventStreamIntegrationTestSupport {

    @Test
    void streamsPersistedEventsFromHighestReconnectCursorAndClosesAtTerminalState() throws Exception {
        Fixture fixture = cancelledGeneration("sse-replay");

        MvcResult stream = openStream(fixture, -1, "0");
        assertThat(stream.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache, no-store");
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");

        MvcResult completed = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store"))
                .andReturn();
        String content = completed.getResponse().getContentAsString();

        assertThat(completed.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(content)
                .contains("id:1", "event:cancelled", "\"sequenceNo\":1")
                .doesNotContain("id:0");
    }

    @Test
    void rejectsUnauthorizedForeignExpiredAndMalformedReplayRequestsBeforeStream() throws Exception {
        Fixture fixture = cancelledGeneration("sse-errors");

        mockMvc.perform(get(fixture.eventsPath()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(authenticated(
                        get(fixture.eventsPath()).accept(MediaType.TEXT_EVENT_STREAM),
                        OTHER_OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION_NOT_FOUND"));

        mockMvc.perform(authenticated(
                        get(fixture.eventsPath())
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .header("Last-Event-ID", "invalid"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_CURSOR"));

        jdbc.update(
                "UPDATE generation_events SET expires_at = ? WHERE generation_id = ? AND sequence_no = 0",
                Timestamp.from(Instant.now().minusSeconds(60)),
                fixture.generationId()
        );
        mockMvc.perform(authenticated(
                        get(fixture.eventsPath()).accept(MediaType.TEXT_EVENT_STREAM),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GENERATION_EVENT_REPLAY_EXPIRED"));
    }
}
