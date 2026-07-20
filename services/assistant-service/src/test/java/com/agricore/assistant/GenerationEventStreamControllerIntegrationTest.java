package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agricore.assistant.provider.model=test-model",
        "agricore.assistant.streaming.max-connections=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationEventStreamControllerIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("41000000-0000-0000-0000-000000000002");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @BeforeEach
    void providerIsAvailable() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
    }
    @Test
    void streamsPersistedEventsFromHighestReconnectCursorAndClosesAtTerminalState() throws Exception {
        Fixture fixture = cancelledGeneration("sse-replay");

        MvcResult stream = mockMvc.perform(authenticated(
                        get(fixture.eventsPath())
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .queryParam("after", "-1")
                                .header("Last-Event-ID", "0"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn();
        String content = completed.getResponse().getContentAsString();

        assertThat(completed.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(content)
                .contains("id:1", "event:cancelled", "\"sequenceNo\":1")
                .doesNotContain("id:0");
    }
    @Test
    void rejectsForeignExpiredAndMalformedReplayRequestsBeforeOpeningStream() throws Exception {
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
    @Test
    void disconnectReleasesCapacityWithoutCancellingGeneration() throws Exception {
        Fixture first = activeGeneration("sse-detach-1");
        MvcResult firstStream = openActiveStream(first);

        firstStream.getRequest().getAsyncContext().complete();

        Fixture second = activeGeneration("sse-detach-2");
        MvcResult secondStream = openActiveStream(second);
        assertThat(generationStatus(first.generationId())).isEqualTo("QUEUED");
        assertThat(cancelRequestedAt(first.generationId())).isNull();

        secondStream.getRequest().getAsyncContext().complete();
        cancel(first);
        cancel(second);
    }

    private MvcResult openActiveStream(Fixture fixture) throws Exception {
        return mockMvc.perform(authenticated(
                        get(fixture.eventsPath())
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .queryParam("after", "0"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private Fixture cancelledGeneration(String key) throws Exception {
        Fixture fixture = activeGeneration(key);
        cancel(fixture);
        return fixture;
    }

    private Fixture activeGeneration(String key) throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", key);
        MvcResult submitted = mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prompt\":\"Stream safely\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID generationId = UUID.fromString(
                objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText());
        return new Fixture(conversationId, generationId);
    }

    private void cancel(Fixture fixture) throws Exception {
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + fixture.conversationId()
                                + "/generations/" + fixture.generationId() + "/cancel"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk());
    }

    private String generationStatus(UUID generationId) {
        return jdbc.queryForObject(
                "SELECT status FROM chat_generations WHERE id = ?", String.class, generationId);
    }

    private Instant cancelRequestedAt(UUID generationId) {
        return jdbc.queryForObject(
                "SELECT cancel_requested_at FROM chat_generations WHERE id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1) == null
                        ? null
                        : resultSet.getTimestamp(1).toInstant(),
                generationId
        );
    }

    private record Fixture(UUID conversationId, UUID generationId) {
        String eventsPath() {
            return CONVERSATIONS_PATH + "/" + conversationId + "/generations/" + generationId + "/events";
        }
    }
}
