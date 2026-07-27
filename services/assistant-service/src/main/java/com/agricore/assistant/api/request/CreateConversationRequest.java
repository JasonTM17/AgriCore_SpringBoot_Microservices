package com.agricore.assistant.api.request;

import com.agricore.assistant.application.model.CreateConversationCommand;
import com.agricore.assistant.domain.model.ConversationContextType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateConversationRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull ConversationContextType contextType,
        UUID farmId
) {
    public CreateConversationCommand toCommand() {
        return new CreateConversationCommand(title, contextType, farmId);
    }
}
