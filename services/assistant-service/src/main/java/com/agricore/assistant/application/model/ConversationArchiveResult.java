package com.agricore.assistant.application.model;

import com.agricore.assistant.domain.model.AssistantConversation;

public record ConversationArchiveResult(
        AssistantConversation conversation,
        boolean changed
) {
}
