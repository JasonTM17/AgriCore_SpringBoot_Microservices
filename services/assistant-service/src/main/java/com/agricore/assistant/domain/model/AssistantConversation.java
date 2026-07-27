package com.agricore.assistant.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantConversation(
        UUID id,
        UUID ownerUserId,
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
    public AssistantConversation {
        roleSnapshot = roleSnapshot == null ? List.of() : List.copyOf(roleSnapshot);
    }
}
