package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class GenerationEventStreamIntegrationTestSupport extends AssistantApiIntegrationTestSupport {

    static final UUID OWNER = UUID.fromString("41000000-0000-0000-0000-000000000001");
    static final UUID OTHER_OWNER = UUID.fromString("41000000-0000-0000-0000-000000000002");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @BeforeEach
    void providerIsAvailable() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
    }

    Fixture cancelledGeneration(String key) throws Exception {
        Fixture fixture = activeGeneration(key);
        cancel(fixture);
        return fixture;
    }

    Fixture activeGeneration(String key) throws Exception {
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

    MvcResult openStream(Fixture fixture, long afterSequence, String lastEventId) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = get(fixture.eventsPath())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .queryParam("after", Long.toString(afterSequence));
        if (lastEventId != null) {
            requestBuilder.header("Last-Event-ID", lastEventId);
        }
        return mockMvc.perform(authenticated(requestBuilder, OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    void cancel(Fixture fixture) throws Exception {
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + fixture.conversationId()
                                + "/generations/" + fixture.generationId() + "/cancel"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk());
    }

    String generationStatus(UUID generationId) {
        return jdbc.queryForObject(
                "SELECT status FROM chat_generations WHERE id = ?", String.class, generationId);
    }

    Instant cancelRequestedAt(UUID generationId) {
        return jdbc.queryForObject(
                "SELECT cancel_requested_at FROM chat_generations WHERE id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1) == null
                        ? null
                        : resultSet.getTimestamp(1).toInstant(),
                generationId
        );
    }

    record Fixture(UUID conversationId, UUID generationId) {
        String eventsPath() {
            return CONVERSATIONS_PATH + "/" + conversationId + "/generations/" + generationId + "/events";
        }
    }
}
