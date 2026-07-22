package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agricore.assistant.provider.model=test-model")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationToolDecisionIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @MockitoBean
    private ToolEvidenceCollector toolEvidenceCollector;

    @BeforeEach
    void configureDependencies() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
        when(toolEvidenceCollector.collect(any()))
                .thenReturn(ToolEvidenceCollection.skipped("TOOLS_DISABLED"));
    }

    @Test
    void auditsScopeDenialBeforeRejectingWithoutGenerationSideEffects() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Denied");
        when(toolEvidenceCollector.collect(any()))
                .thenReturn(ToolEvidenceCollection.denied("TOOL_SCOPE_UNAVAILABLE", 8));

        submit(conversationId, "denied-key", "Private context")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOOL_SCOPE_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Integer.class
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT reason_code FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                String.class
        )).isEqualTo("TOOL_SCOPE_UNAVAILABLE");
        assertThat(jdbc.queryForObject(
                "SELECT outcome FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                String.class
        )).isEqualTo("DENIED");
        verifyNoInteractions(workDispatcher);
    }

    @Test
    void rejectsArchivedConversationBeforeOutboundToolCollection() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Archived");
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/archive"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk());

        submit(conversationId, "archived-key", "Should not collect context")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_OPEN"));

        verifyNoInteractions(toolEvidenceCollector, workDispatcher);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events "
                        + "WHERE action = 'GENERATION_SUBMISSION_REJECTED' "
                        + "AND reason_code = 'CONVERSATION_NOT_OPEN'",
                Integer.class
        )).isOne();
    }

    @Test
    void rejectsAnActiveConversationBeforeRepeatingOutboundToolCollection() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Active");
        submit(conversationId, "first-key", "First request")
                .andExpect(status().isAccepted());

        submit(conversationId, "second-key", "Second request")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION_ALREADY_ACTIVE"));

        verify(toolEvidenceCollector).collect(any());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Integer.class
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT generation_id IS NOT NULL FROM assistant_audit_events "
                        + "WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Boolean.class
        )).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events "
                        + "WHERE action = 'GENERATION_SUBMISSION_REJECTED' "
                        + "AND reason_code = 'GENERATION_ALREADY_ACTIVE'",
                Integer.class
        )).isOne();
    }

    @Test
    void concurrentSameKeyKeepsGenerationAndToolAuditIdempotent() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Concurrent");
        CountDownLatch bothCollecting = new CountDownLatch(2);
        CountDownLatch releaseCollection = new CountDownLatch(1);
        AtomicInteger collectionSequence = new AtomicInteger();
        when(toolEvidenceCollector.collect(any())).thenAnswer(invocation -> {
            int sequence = collectionSequence.getAndIncrement();
            bothCollecting.countDown();
            if (!releaseCollection.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent collection barrier timed out");
            }
            return sequence == 0
                    ? collected("FARM-1", ToolSource.FARM, "name", "North farm")
                    : collected("PLOT-1", ToolSource.PLOT, "area", "12.50");
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = executor.submit(() ->
                    submit(conversationId, "same-key", "Same request").andReturn());
            Future<MvcResult> second = executor.submit(() ->
                    submit(conversationId, "same-key", "Same request").andReturn());

            assertThat(bothCollecting.await(5, TimeUnit.SECONDS)).isTrue();
            releaseCollection.countDown();
            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(
                    firstResult.getResponse().getStatus(),
                    secondResult.getResponse().getStatus()
            )).containsExactlyInAnyOrder(200, 202);
            var firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString());
            var secondBody = objectMapper.readTree(secondResult.getResponse().getContentAsString());
            assertThat(firstBody.get("id").asText()).isEqualTo(secondBody.get("id").asText());
            assertThat(firstBody.get("deduplicated").asBoolean())
                    .isNotEqualTo(secondBody.get("deduplicated").asBoolean());
        } finally {
            releaseCollection.countDown();
        }

        verify(toolEvidenceCollector, times(2)).collect(any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Integer.class
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT generation_id IS NOT NULL FROM assistant_audit_events "
                        + "WHERE action = 'TOOL_EVIDENCE_DECISION'",
                Boolean.class
        )).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events "
                        + "WHERE action = 'TOOL_EVIDENCE_ATTEMPT' "
                        + "AND reason_code = 'IDEMPOTENT_REPLAY' AND generation_id IS NULL",
                Integer.class
        )).isOne();
        assertWinnerEvidenceMatchesLinkedDecision();
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            UUID conversationId,
            String idempotencyKey,
            String prompt
    ) throws Exception {
        return mockMvc.perform(authenticated(
                post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("prompt", prompt))),
                OWNER,
                "FIELD_WORKER"
        ));
    }

    private ToolEvidenceCollection collected(
            String citationId,
            ToolSource source,
            String field,
            String value
    ) {
        return ToolEvidenceCollection.collected(new ToolEvidenceSnapshot(List.of(
                new ToolFact(citationId, source, Map.of(field, value))
        )), 3);
    }

    private void assertWinnerEvidenceMatchesLinkedDecision() {
        String persistedEvidence = jdbc.queryForObject(
                "SELECT tool_evidence FROM chat_generations", String.class);
        String linkedMetadata = jdbc.queryForObject(
                "SELECT metadata FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_DECISION'",
                String.class
        );
        String discardedMetadata = jdbc.queryForObject(
                "SELECT metadata FROM assistant_audit_events WHERE action = 'TOOL_EVIDENCE_ATTEMPT'",
                String.class
        );
        if (persistedEvidence.contains("FARM")) {
            assertThat(linkedMetadata).contains("FARM").doesNotContain("PLOT");
            assertThat(discardedMetadata).contains("PLOT").doesNotContain("FARM");
        } else {
            assertThat(persistedEvidence).contains("PLOT");
            assertThat(linkedMetadata).contains("PLOT").doesNotContain("FARM");
            assertThat(discardedMetadata).contains("FARM").doesNotContain("PLOT");
        }
    }
}
