package com.agricore.assistant.infrastructure.budget;

import com.agricore.assistant.application.port.AssistantRequestBudget;
import com.agricore.assistant.domain.model.AssistantActor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "agricore.assistant.budget",
        name = "enabled",
        havingValue = "false"
)
public class DisabledAssistantRequestBudget implements AssistantRequestBudget {

    @Override
    public void reserve(AssistantActor actor, String clientIp, String reservationId, int desiredTotalTokens) {
        // Explicitly disabled only by local/test configuration.
    }
}
