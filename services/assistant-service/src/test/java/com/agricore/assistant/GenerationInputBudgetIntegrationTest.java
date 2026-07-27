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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agricore.assistant.provider.model=test-model",
        "agricore.assistant.provider.max-input-characters=1024"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationInputBudgetIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @MockitoBean
    private ChatProvider chatProvider;

    @MockitoBean
    private GenerationWorkDispatcher workDispatcher;

    @MockitoBean
    private ToolEvidenceCollector toolEvidenceCollector;

    @BeforeEach
    void providerIsAvailable() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("test", true, true, null));
    }

    @Test
    void rejectsPromptThatCannotFitBesideTheRequiredSystemPolicy() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Budget");
        String requestBody = objectMapper.writeValueAsString(Map.of("prompt", "x".repeat(900)));

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "generation-budget")
                                .contentType("application/json")
                                .content(requestBody),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages", Integer.class)).isZero();
        verifyNoInteractions(workDispatcher);
    }

    @Test
    void auditsCollectedEvidenceWhenItsSizeCausesBudgetRejection() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Evidence budget");
        ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(new ToolFact(
                "FARM-1",
                ToolSource.FARM,
                Map.of("description", "x".repeat(256))
        )));
        when(toolEvidenceCollector.collect(any(), any()))
                .thenReturn(ToolEvidenceCollection.collected(evidence, 4));
        String requestBody = objectMapper.writeValueAsString(Map.of("prompt", "x".repeat(300)));

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/generations")
                                .header("Idempotency-Key", "evidence-budget")
                                .contentType("application/json")
                                .content(requestBody),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verify(toolEvidenceCollector).collect(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events "
                        + "WHERE action = 'TOOL_EVIDENCE_ATTEMPT' "
                        + "AND reason_code = 'INPUT_BUDGET_EXCEEDED'",
                Integer.class
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT generation_id IS NULL FROM assistant_audit_events "
                        + "WHERE action = 'TOOL_EVIDENCE_ATTEMPT'",
                Boolean.class
        )).isTrue();
        verifyNoInteractions(workDispatcher);
    }
}
