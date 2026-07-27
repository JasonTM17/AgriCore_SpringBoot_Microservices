package com.agricore.assistant.api.response;

import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String title,
        ConversationContextType contextType,
        UUID farmId,
        ConversationStatus status,
        List<String> roleSnapshot,
        long nextMessageSequence,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        Instant purgeAfter
) {
    public static ConversationResponse from(AssistantConversation conversation) {
        return new ConversationResponse(
                conversation.id(),
                conversation.title(),
                conversation.contextType(),
                conversation.farmId(),
                conversation.status(),
                conversation.roleSnapshot(),
                conversation.nextMessageSequence(),
                conversation.version(),
                conversation.createdAt(),
                conversation.updatedAt(),
                conversation.archivedAt(),
                conversation.purgeAfter()
        );
    }
}
