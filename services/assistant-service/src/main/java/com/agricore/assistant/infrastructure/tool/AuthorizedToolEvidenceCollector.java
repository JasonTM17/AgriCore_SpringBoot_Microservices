package com.agricore.assistant.infrastructure.tool;

import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.port.KnowledgeRetriever;
import com.agricore.assistant.application.port.ToolCollectionException;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.infrastructure.configuration.AssistantRagProperties;
import com.agricore.assistant.infrastructure.configuration.AssistantToolProperties;
import com.agricore.assistant.infrastructure.tool.farm.FarmReadToolClient;
import com.agricore.farmaccess.FarmAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Component
public class AuthorizedToolEvidenceCollector implements ToolEvidenceCollector {

    private static final int MAX_EVIDENCE_FACTS = 25;

    private final AssistantToolProperties properties;
    private final ConversationContextAccess contextAccess;
    private final FarmReadToolClient farmClient;
    private final AssistantRagProperties ragProperties;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LongSupplier nanoTime;

    @Autowired
    public AuthorizedToolEvidenceCollector(
            AssistantToolProperties properties,
            ConversationContextAccess contextAccess,
            FarmReadToolClient farmClient,
            AssistantRagProperties ragProperties,
            KnowledgeRetriever knowledgeRetriever
    ) {
        this(
                properties,
                contextAccess,
                farmClient,
                ragProperties,
                knowledgeRetriever,
                System::nanoTime
        );
    }

    AuthorizedToolEvidenceCollector(
            AssistantToolProperties properties,
            ConversationContextAccess contextAccess,
            FarmReadToolClient farmClient,
            AssistantRagProperties ragProperties,
            KnowledgeRetriever knowledgeRetriever,
            LongSupplier nanoTime
    ) {
        this.properties = Objects.requireNonNull(properties, "tool properties are required");
        this.contextAccess = Objects.requireNonNull(contextAccess, "context access is required");
        this.farmClient = Objects.requireNonNull(farmClient, "farm tool client is required");
        this.ragProperties = Objects.requireNonNull(ragProperties, "RAG properties are required");
        this.knowledgeRetriever = Objects.requireNonNull(
                knowledgeRetriever, "knowledge retriever is required");
        this.nanoTime = Objects.requireNonNull(nanoTime, "monotonic clock is required");
    }

    @Override
    public ToolEvidenceCollection collect(AssistantConversation conversation, String prompt) {
        Objects.requireNonNull(conversation, "conversation is required");
        long startedAt = nanoTime.getAsLong();
        try {
            List<ToolFact> facts = new ArrayList<>(collectFarmEvidence(conversation));
            int remainingCapacity = MAX_EVIDENCE_FACTS - facts.size();
            if (remainingCapacity > 0) {
                knowledgeRetriever.retrieve(prompt).stream()
                        .limit(remainingCapacity)
                        .forEach(facts::add);
            }
            if (!facts.isEmpty()) {
                return ToolEvidenceCollection.collected(
                        new ToolEvidenceSnapshot(facts),
                        elapsedMillis(startedAt)
                );
            }
            return ToolEvidenceCollection.skipped(emptyReason(conversation));
        } catch (FarmAccessException exception) {
            return ToolEvidenceCollection.denied(
                    farmAccessReason(exception),
                    elapsedMillis(startedAt)
            );
        } catch (ToolCollectionException exception) {
            long latencyMs = elapsedMillis(startedAt);
            if ("TOOL_SCOPE_UNAVAILABLE".equals(exception.reasonCode())
                    || "TOOL_AUTHORIZATION_UNAVAILABLE".equals(exception.reasonCode())) {
                return ToolEvidenceCollection.denied(exception.reasonCode(), latencyMs);
            }
            return ToolEvidenceCollection.unavailable(exception.reasonCode(), latencyMs);
        }
    }

    private List<ToolFact> collectFarmEvidence(AssistantConversation conversation) {
        if (!properties.isEnabled() || conversation.contextType() == ConversationContextType.ENTERPRISE) {
            return List.of();
        }
        if (conversation.farmId() == null) {
            throw AssistantException.invalidContext();
        }
        contextAccess.requireFarmAccess(conversation.farmId());
        return farmClient.collect(conversation.farmId()).facts();
    }

    private String emptyReason(AssistantConversation conversation) {
        if (ragProperties.isEnabled()) {
            return "RAG_NO_MATCH";
        }
        if (!properties.isEnabled()) {
            return "TOOLS_DISABLED";
        }
        if (conversation.contextType() == ConversationContextType.ENTERPRISE) {
            return "TOOL_CONTEXT_NOT_APPLICABLE";
        }
        return "EVIDENCE_NOT_FOUND";
    }

    private static String farmAccessReason(FarmAccessException exception) {
        return switch (exception.getCode()) {
            case "FARM_ACCESS_DENIED", "FARM_RESOURCE_NOT_FOUND" -> "TOOL_SCOPE_UNAVAILABLE";
            default -> "TOOL_AUTHORIZATION_UNAVAILABLE";
        };
    }

    private long elapsedMillis(long startedAt) {
        long elapsedNanos = Math.max(0, nanoTime.getAsLong() - startedAt);
        return Math.min(60_000, TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
    }
}
