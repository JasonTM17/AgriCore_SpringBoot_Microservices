package com.agricore.assistant.infrastructure.tool;

import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.port.ToolCollectionException;
import com.agricore.assistant.application.port.ToolEvidenceCollector;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.infrastructure.configuration.AssistantToolProperties;
import com.agricore.assistant.infrastructure.tool.farm.FarmReadToolClient;
import com.agricore.farmaccess.FarmAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Component
public class AuthorizedToolEvidenceCollector implements ToolEvidenceCollector {

    private final AssistantToolProperties properties;
    private final ConversationContextAccess contextAccess;
    private final FarmReadToolClient farmClient;
    private final LongSupplier nanoTime;

    @Autowired
    public AuthorizedToolEvidenceCollector(
            AssistantToolProperties properties,
            ConversationContextAccess contextAccess,
            FarmReadToolClient farmClient
    ) {
        this(properties, contextAccess, farmClient, System::nanoTime);
    }

    AuthorizedToolEvidenceCollector(
            AssistantToolProperties properties,
            ConversationContextAccess contextAccess,
            FarmReadToolClient farmClient,
            LongSupplier nanoTime
    ) {
        this.properties = Objects.requireNonNull(properties, "tool properties are required");
        this.contextAccess = Objects.requireNonNull(contextAccess, "context access is required");
        this.farmClient = Objects.requireNonNull(farmClient, "farm tool client is required");
        this.nanoTime = Objects.requireNonNull(nanoTime, "monotonic clock is required");
    }

    @Override
    public ToolEvidenceCollection collect(AssistantConversation conversation) {
        Objects.requireNonNull(conversation, "conversation is required");
        if (!properties.isEnabled()) {
            return ToolEvidenceCollection.skipped("TOOLS_DISABLED");
        }
        if (conversation.contextType() == ConversationContextType.ENTERPRISE) {
            return ToolEvidenceCollection.skipped("TOOL_CONTEXT_NOT_APPLICABLE");
        }
        if (conversation.farmId() == null) {
            throw AssistantException.invalidContext();
        }

        long startedAt = nanoTime.getAsLong();
        try {
            contextAccess.requireFarmAccess(conversation.farmId());
            return ToolEvidenceCollection.collected(
                    farmClient.collect(conversation.farmId()),
                    elapsedMillis(startedAt)
            );
        } catch (FarmAccessException ex) {
            return ToolEvidenceCollection.denied(
                    farmAccessReason(ex),
                    elapsedMillis(startedAt)
            );
        } catch (ToolCollectionException ex) {
            long latencyMs = elapsedMillis(startedAt);
            if ("TOOL_SCOPE_UNAVAILABLE".equals(ex.reasonCode())
                    || "TOOL_AUTHORIZATION_UNAVAILABLE".equals(ex.reasonCode())) {
                return ToolEvidenceCollection.denied(ex.reasonCode(), latencyMs);
            }
            return ToolEvidenceCollection.unavailable(ex.reasonCode(), latencyMs);
        }
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
