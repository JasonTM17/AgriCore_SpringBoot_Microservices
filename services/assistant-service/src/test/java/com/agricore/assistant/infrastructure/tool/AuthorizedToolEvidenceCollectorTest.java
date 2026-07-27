package com.agricore.assistant.infrastructure.tool;

import com.agricore.assistant.application.model.ToolCollectionOutcome;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.port.KnowledgeRetriever;
import com.agricore.assistant.application.port.ToolCollectionException;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.assistant.infrastructure.configuration.AssistantRagProperties;
import com.agricore.assistant.infrastructure.configuration.AssistantToolProperties;
import com.agricore.assistant.infrastructure.tool.farm.FarmReadToolClient;
import com.agricore.farmaccess.FarmAccessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizedToolEvidenceCollectorTest {

    private final AssistantToolProperties properties = new AssistantToolProperties();
    private final ConversationContextAccess contextAccess = mock(ConversationContextAccess.class);
    private final FarmReadToolClient farmClient = mock(FarmReadToolClient.class);
    private final AssistantRagProperties ragProperties = new AssistantRagProperties();
    private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
    private final AtomicLong nanoTime = new AtomicLong();
    private final AuthorizedToolEvidenceCollector collector = new AuthorizedToolEvidenceCollector(
            properties,
            contextAccess,
            farmClient,
            ragProperties,
            knowledgeRetriever,
            () -> nanoTime.getAndAdd(5_000_000)
    );

    @Test
    void skipsDisabledAndEnterpriseContextsWithoutOutboundCalls() {
        when(knowledgeRetriever.retrieve("status")).thenReturn(List.of());
        var disabled = collector.collect(
                conversation(ConversationContextType.FARM, UUID.randomUUID()), "status");
        properties.setEnabled(true);
        var enterprise = collector.collect(
                conversation(ConversationContextType.ENTERPRISE, null), "status");

        assertThat(disabled.reasonCode()).isEqualTo("TOOLS_DISABLED");
        assertThat(enterprise.reasonCode()).isEqualTo("TOOL_CONTEXT_NOT_APPLICABLE");
        verify(contextAccess, never()).requireFarmAccess(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revalidatesFarmScopeAndReturnsBoundedEvidence() {
        properties.setEnabled(true);
        UUID farmId = UUID.randomUUID();
        ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(
                new ToolFact("FARM-1", ToolSource.FARM, Map.of("status", "ACTIVE"))
        ));
        when(farmClient.collect(farmId)).thenReturn(evidence);

        when(knowledgeRetriever.retrieve("status")).thenReturn(List.of());

        var result = collector.collect(conversation(ConversationContextType.FARM, farmId), "status");

        assertThat(result.outcome()).isEqualTo(ToolCollectionOutcome.COLLECTED);
        assertThat(result.evidence()).isEqualTo(evidence);
        assertThat(result.latencyMs()).isEqualTo(5);
        verify(contextAccess).requireFarmAccess(farmId);
    }

    @Test
    void degradesDependencyFailuresButClassifiesAmbiguousScope() {
        properties.setEnabled(true);
        UUID farmId = UUID.randomUUID();
        when(farmClient.collect(farmId))
                .thenThrow(ToolCollectionException.responseInvalid())
                .thenThrow(ToolCollectionException.scopeUnavailable());

        var unavailable = collector.collect(
                conversation(ConversationContextType.FARM, farmId), "status");

        assertThat(unavailable.outcome()).isEqualTo(ToolCollectionOutcome.UNAVAILABLE);
        assertThat(unavailable.reasonCode()).isEqualTo("TOOL_RESPONSE_INVALID");
        var denied = collector.collect(conversation(ConversationContextType.FARM, farmId), "status");
        assertThat(denied.outcome()).isEqualTo(ToolCollectionOutcome.DENIED);
        assertThat(denied.reasonCode()).isEqualTo("TOOL_SCOPE_UNAVAILABLE");
    }

    @Test
    void normalizesFarmAccessDenialBeforeToolDispatch() {
        properties.setEnabled(true);
        UUID farmId = UUID.randomUUID();
        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "denied", 403))
                .when(contextAccess).requireFarmAccess(farmId);

        var denied = collector.collect(conversation(ConversationContextType.FARM, farmId), "status");

        assertThat(denied.outcome()).isEqualTo(ToolCollectionOutcome.DENIED);
        assertThat(denied.reasonCode()).isEqualTo("TOOL_SCOPE_UNAVAILABLE");
        verify(farmClient, never()).collect(farmId);
    }

    @Test
    void mergesCuratedKnowledgeAfterAuthorizedFarmFacts() {
        properties.setEnabled(true);
        ragProperties.setEnabled(true);
        UUID farmId = UUID.randomUUID();
        ToolFact farm = new ToolFact(
                "FARM-1", ToolSource.FARM, Map.of("status", "ACTIVE"));
        ToolFact knowledge = new ToolFact(
                "KB-1",
                ToolSource.KNOWLEDGE,
                Map.of("title", "Inventory safety", "excerpt", "Never allow negative stock")
        );
        when(farmClient.collect(farmId)).thenReturn(new ToolEvidenceSnapshot(List.of(farm)));
        when(knowledgeRetriever.retrieve("How should stock be reserved?"))
                .thenReturn(List.of(knowledge));

        var result = collector.collect(
                conversation(ConversationContextType.FARM, farmId),
                "How should stock be reserved?"
        );

        assertThat(result.outcome()).isEqualTo(ToolCollectionOutcome.COLLECTED);
        assertThat(result.evidence().facts()).containsExactly(farm, knowledge);
    }

    @Test
    void returnsKnowledgeForEnterpriseContextWithoutFarmEgress() {
        ragProperties.setEnabled(true);
        ToolFact knowledge = new ToolFact(
                "KB-1",
                ToolSource.KNOWLEDGE,
                Map.of("title", "Assistant boundary")
        );
        when(knowledgeRetriever.retrieve("assistant security")).thenReturn(List.of(knowledge));

        var result = collector.collect(
                conversation(ConversationContextType.ENTERPRISE, null),
                "assistant security"
        );

        assertThat(result.outcome()).isEqualTo(ToolCollectionOutcome.COLLECTED);
        assertThat(result.evidence().facts()).containsExactly(knowledge);
        verify(contextAccess, never()).requireFarmAccess(org.mockito.ArgumentMatchers.any());
    }

    private AssistantConversation conversation(ConversationContextType contextType, UUID farmId) {
        return new AssistantConversation(
                UUID.randomUUID(), UUID.randomUUID(), "Assistant", contextType, farmId,
                ConversationStatus.OPEN, List.of("FARM_MANAGER"), 0, 0,
                Instant.EPOCH, Instant.EPOCH, null, null
        );
    }
}
