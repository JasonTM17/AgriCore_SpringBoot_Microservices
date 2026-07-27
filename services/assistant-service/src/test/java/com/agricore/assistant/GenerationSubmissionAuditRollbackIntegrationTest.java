package com.agricore.assistant;

import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agricore.assistant.provider.model=test-model")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationSubmissionAuditRollbackIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private ToolEvidenceCollector toolEvidenceCollector;

    @MockitoBean
    private AssistantAuditRepository auditRepository;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @BeforeEach
    void configureFailureAtTheTransactionalAuditBoundary() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
        when(toolEvidenceCollector.collect(any(), any()))
                .thenReturn(ToolEvidenceCollection.skipped("TOOLS_DISABLED"));
        doAnswer(invocation -> {
            AssistantAuditEvent event = invocation.getArgument(0);
            if ("TOOL_EVIDENCE_DECISION".equals(event.action())) {
                throw new IllegalStateException("forced audit persistence failure");
            }
            return null;
        }).when(auditRepository).save(any());
    }

    @Test
    void rollsBackGenerationMessageAndEventWhenLinkedAuditFails() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Rollback");

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "audit-rollback")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "prompt", "Will this roll back?"
                                ))),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generation_events", Integer.class)).isZero();
        verifyNoInteractions(workDispatcher);
    }
}
