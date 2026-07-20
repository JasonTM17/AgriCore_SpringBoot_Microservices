package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.model.ConversationContextType;

import java.util.UUID;

public record CreateConversationCommand(
        String title,
        ConversationContextType contextType,
        UUID farmId
) {
}
