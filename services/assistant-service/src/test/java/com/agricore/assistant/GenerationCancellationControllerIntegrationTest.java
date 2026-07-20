package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationExecutionRepository;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agricore.assistant.provider.model=test-model")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationCancellationControllerIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("31000000-0000-0000-0000-000000000002");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @Autowired
    private GenerationExecutionRepository executionRepository;

    @BeforeEach
    void providerIsAvailable() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
    }

    @Test
    void ownerCancellationOfQueuedWorkIsImmediateTerminalAndIdempotent() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Queued cancel");
        UUID generationId = submit(conversationId, "queued-cancel", OWNER);

        cancel(conversationId, generationId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        cancel(conversationId, generationId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(workDispatcher).dispatchAfterCommit(generationId);
        verify(workDispatcher, never()).cancelAfterCommit(generationId);
        assertThat(cancellationAuditCount(generationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generation_events WHERE generation_id = ? AND event_type = 'CANCELLED'",
                Integer.class,
                generationId
        )).isEqualTo(1);
    }

    @Test
    void runningCancellationIsCommittedBeforeTheWorkerIsSignalled() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Running cancel");
        UUID generationId = submit(conversationId, "running-cancel", OWNER);
        UUID leaseToken = UUID.randomUUID();
        Instant now = Instant.now();
        executionRepository.claim(
                generationId,
                leaseToken,
                now,
                now.plusSeconds(30),
                now.plus(Duration.ofHours(24))
        );

        cancel(conversationId, generationId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));
        cancel(conversationId, generationId, OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));

        verify(workDispatcher, times(2)).cancelAfterCommit(generationId);
        assertThat(cancellationAuditCount(generationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generation_events WHERE generation_id = ? AND event_type = 'STATUS'",
                Integer.class,
                generationId
        )).isEqualTo(3);
    }

    @Test
    void foreignOwnerGetsSafeNotFoundAndCannotCancelTheGeneration() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Private cancel");
        UUID generationId = submit(conversationId, "foreign-cancel", OWNER);

        cancel(conversationId, generationId, OTHER_OWNER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        verify(workDispatcher, never()).cancelAfterCommit(generationId);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_generations WHERE id = ?",
                String.class,
                generationId
        )).isEqualTo("QUEUED");
    }

    private UUID submit(UUID conversationId, String idempotencyKey, UUID owner) throws Exception {
        var result = mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType("application/json")
                                .content("{\"prompt\":\"Cancel this generation\"}"),
                        owner,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isAccepted())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions cancel(
            UUID conversationId,
            UUID generationId,
            UUID owner
    ) throws Exception {
        return mockMvc.perform(authenticated(
                post(CONVERSATIONS_PATH + "/" + conversationId
                        + "/generations/" + generationId + "/cancel"),
                owner,
                "FIELD_WORKER"
        ));
    }

    private int cancellationAuditCount(UUID generationId) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM assistant_audit_events
                 WHERE generation_id = ?
                   AND action = 'GENERATION_CANCELLATION_REQUESTED'
                """,
                Integer.class,
                generationId
        );
    }
}
