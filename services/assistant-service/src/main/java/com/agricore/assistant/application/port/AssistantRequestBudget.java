package com.agricore.assistant.application.port;

import com.agricore.assistant.domain.model.AssistantActor;

public interface AssistantRequestBudget {

    void reserve(AssistantActor actor, String clientIp, int estimatedInputTokens);

    void reserveAdditionalTokens(AssistantActor actor, String clientIp, int additionalInputTokens);
}
