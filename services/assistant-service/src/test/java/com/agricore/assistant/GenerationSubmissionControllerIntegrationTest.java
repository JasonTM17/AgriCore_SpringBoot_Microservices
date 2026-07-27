package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agricore.assistant.provider.model=test-model")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationSubmissionControllerIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("30000000-0000-0000-0000-000000000002");

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
    void submitIsServerIdempotentAndExposesOnlySafeGenerationMetadata() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Assistant");

        var first = mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-1")
                                .contentType("application/json")
                                .content("{\"prompt\":\"How is the crop?\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.provider").value("test"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.deduplicated").value(false))
                .andExpect(jsonPath("$.userMessageId").isNotEmpty())
                .andExpect(jsonPath("$.requestHash").doesNotExist())
                .andExpect(jsonPath("$.prompt").doesNotExist())
                .andReturn();
        UUID generationId = UUID.fromString(objectMapper.readTree(
                first.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-1")
                                .contentType("application/json")
                                .content("{\"prompt\":\"How is the crop?\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(generationId.toString()))
                .andExpect(jsonPath("$.deduplicated").value(true));

        verify(workDispatcher).dispatchAfterCommit(generationId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isEqualTo(1);
    }

    @Test
    void keyReuseWithDifferentPromptAndMissingHeaderAreSafeClientErrors() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Assistant");
        submit(conversationId, "generation-2", "First prompt", OWNER);

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-2")
                                .contentType("application/json")
                                .content("{\"prompt\":\"Different prompt\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .contentType("application/json")
                                .content("{\"prompt\":\"No key\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void unavailableProviderReturns503WithoutPersistingPromptOrGeneration() throws Exception {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("none", false, false, "AI_PROVIDER_UNAVAILABLE"));
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Assistant");

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-3")
                                .contentType("application/json")
                                .content("{\"prompt\":\"Will not persist\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("The configured AI provider is unavailable"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isZero();
    }

    @Test
    void idempotentRetryReturnsExistingGenerationEvenIfProviderLaterGoesUnavailable() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Retry");
        String generationId = submit(conversationId, "generation-retry", "Persisted request", OWNER);
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("none", false, false, "AI_PROVIDER_UNAVAILABLE"));

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-retry")
                                .contentType("application/json")
                                .content("{\"prompt\":\"Persisted request\"}"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(generationId))
                .andExpect(jsonPath("$.deduplicated").value(true));
    }

    @Test
    void inaccessibleConversationReturnsNotFoundBeforeProviderWork() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Private");

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-4")
                                .contentType("application/json")
                                .content("{\"prompt\":\"Private\"}"),
                        OTHER_OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void ownerCanReadQueuedGenerationAndInitialEvent() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Events");
        String generationId = submit(conversationId, "generation-5", "Read events", OWNER);

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH + "/" + conversationId + "/generations/" + generationId),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH + "/" + conversationId + "/generations/" + generationId + "/events")
                                .queryParam("after", "-1"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequenceNo").value(0))
                .andExpect(jsonPath("$[0].eventType").value("STATUS"));
    }

    private String submit(UUID conversationId, String key, String prompt, UUID owner) throws Exception {
        var result = mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content("{\"prompt\":\"" + prompt + "\"}"),
                        owner,
                        "FIELD_WORKER"
                )).andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
